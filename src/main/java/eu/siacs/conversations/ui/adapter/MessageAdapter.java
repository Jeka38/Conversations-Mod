package eu.siacs.conversations.ui.adapter;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.format.DateUtils;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;

import com.google.common.base.Strings;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Message.FileParams;
import eu.siacs.conversations.entities.RtpSessionStatus;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.DraggableListView;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.service.AudioPlayer;
import eu.siacs.conversations.ui.text.DividerSpan;
import eu.siacs.conversations.ui.text.QuoteSpan;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.MyLinkify;
import eu.siacs.conversations.ui.util.QuoteHelper;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.ui.util.ViewUtil;
import eu.siacs.conversations.ui.widget.ClickableMovementMethod;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.mam.MamReference;

public class MessageAdapter extends ArrayAdapter<Message> implements DraggableListView.DraggableAdapter {

    public static final String DATE_SEPARATOR_BODY = "DATE_SEPARATOR";
    private static final int SENT = 0;
    private static final int RECEIVED = 1;
    private static final int STATUS = 2;
    private static final int DATE_SEPARATOR = 3;
    private static final int RTP_SESSION = 4;
    private final XmppActivity activity;
    private final AudioPlayer audioPlayer;
    private List<String> highlightedTerm = null;
    private final DisplayMetrics metrics;
    private OnContactPictureClicked mOnContactPictureClickedListener;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener;
    private MessageEmptyPartClickListener messageEmptyPartClickListener;
    private SelectionStatusProvider selectionStatusProvider;
    private MessageBoxSwipedListener messageBoxSwipedListener;
    private ReplyClickListener replyClickListener;
    private boolean mUseGreenBackground = false;
    private final boolean mForceNames;

    @ColorInt
    private int primaryColor = -1;

    private boolean allowRelativeTimestamps = true;

    private ViewDragHelper dragHelper = null;
    private final ViewDragHelper.Callback dragCallback = new ViewDragHelper.Callback() {
        private int horizontalOffset = 0;
        private boolean swipedEnoughFirstTime = true;

        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
            return child.getTag(R.id.TAG_DRAGGABLE) != null;
        }

        @Override
        public void onViewCaptured(@NonNull View capturedChild, int activePointerId) {
            horizontalOffset = 0;
            swipedEnoughFirstTime = true;
            super.onViewCaptured(capturedChild, activePointerId);
        }

        @Override
        public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
            if (dragHelper != null) {
                dragHelper.settleCapturedViewAt(0, releasedChild.getTop());
                ViewCompat.postOnAnimation(releasedChild, new SettleRunnable(releasedChild));
                ViewHolder viewHolder = (ViewHolder) releasedChild.getTag();

                if (viewHolder != null && viewHolder.position >= 0 && viewHolder.position < getCount() && horizontalOffset < -releasedChild.getWidth()/6) {
                    Message m = getItem(viewHolder.position);
                    if (messageBoxSwipedListener != null) {
                        messageBoxSwipedListener.onMessageBoxReleasedAfterSwipe(m);
                    }
                }
            }
            horizontalOffset = 0;
            swipedEnoughFirstTime = true;
            super.onViewReleased(releasedChild, xvel, yvel);
        }

        @Override
        public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
            int fixedLeft = Math.min(left, 0);
            horizontalOffset = fixedLeft;

            if (horizontalOffset < -child.getWidth()/6 && swipedEnoughFirstTime) {
                swipedEnoughFirstTime = false;
                messageBoxSwipedListener.onMessageBoxSwipedEnough();
            }

            return Math.max(-child.getWidth()/4, fixedLeft);
        }


        @Override
        public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
            return child.getTop();
        }

        @Override
        public int getViewHorizontalDragRange(@NonNull View child) {
            return Math.max(Math.abs(child.getLeft()), 1);
        }

        private class SettleRunnable implements Runnable {
            private View view;

            public SettleRunnable(View view) {
                this.view = view;
            }

            @Override
            public void run() {
                if (dragHelper != null && dragHelper.continueSettling(true)) {
                    ViewCompat.postOnAnimation(view, this);
                }
            }
        }
    };

    public MessageAdapter(final XmppActivity activity, final List<Message> messages, final boolean forceNames) {
        super(activity, 0, messages);
        this.audioPlayer = new AudioPlayer(this);
        this.activity = activity;
        metrics = getContext().getResources().getDisplayMetrics();
        updatePreferences();
        this.mForceNames = forceNames;

        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        allowRelativeTimestamps = !p.getBoolean("always_full_timestamps", activity.getResources().getBoolean(R.bool.always_full_timestamps));
    }

    public MessageAdapter(final XmppActivity activity, final List<Message> messages) {
        this(activity, messages, false);
    }

    @Nullable
    @Override
    public ViewDragHelper.Callback getDragCallback() {
        return dragCallback;
    }

    @Override
    public void setViewDragHelper(@Nullable ViewDragHelper helper) {
        this.dragHelper = helper;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        boolean enabled;

        switch (getItemViewType(position)) {
            case SENT:
            case RECEIVED:
                enabled = true;
                break;
            default:
                enabled = false;
        }

        return enabled;
    }


    private static void resetClickListener(View... views) {
        for (View view : views) {
            view.setOnClickListener(null);
        }
    }

    public void flagScreenOn() {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void flagScreenOff() {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void setVolumeControl(final int stream) {
        activity.setVolumeControlStream(stream);
    }

    public void setOnContactPictureClicked(OnContactPictureClicked listener) {
        this.mOnContactPictureClickedListener = listener;
    }

    public Activity getActivity() {
        return activity;
    }

    public void setOnContactPictureLongClicked(
            OnContactPictureLongClicked listener) {
        this.mOnContactPictureLongClickedListener = listener;
    }

    public void setMessageEmptyPartLongClickListener(
            MessageEmptyPartClickListener listener) {
        this.messageEmptyPartClickListener = listener;
    }

    public void setSelectionStatusProvider(
            SelectionStatusProvider provider) {
        this.selectionStatusProvider = provider;
    }

    public void setOnMessageBoxSwiped(MessageBoxSwipedListener listener) {
        this.messageBoxSwipedListener = listener;
    }

    public void setReplyClickListener(ReplyClickListener listener) {
        this.replyClickListener = listener;
    }

    @Override
    public int getViewTypeCount() {
        return 5;
    }

    private int getItemViewType(Message message) {
        if (message.getType() == Message.TYPE_STATUS) {
            if (DATE_SEPARATOR_BODY.equals(message.getBody())) {
                return DATE_SEPARATOR;
            } else {
                return STATUS;
            }
        } else if (message.getType() == Message.TYPE_RTP_SESSION) {
            return RTP_SESSION;
        } else if (message.getStatus() <= Message.STATUS_RECEIVED) {
            return RECEIVED;
        } else {
            return SENT;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return this.getItemViewType(getItem(position));
    }

    public int getMessageTextColor(boolean onDark, boolean primary) {
        if (onDark) {
            return ContextCompat.getColor(activity, primary ? R.color.white : R.color.white70);
        } else {
            return ContextCompat.getColor(activity, primary ? R.color.black87 : R.color.black54);
        }
    }

    private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean darkBackground) {
        String filesize = null;
        String info = null;
        boolean error = false;
        if (viewHolder.indicatorReceived != null) {
            viewHolder.indicatorReceived.setVisibility(View.GONE);
        }

        if (viewHolder.edit_indicator != null) {
            if (message.edited()) {
                viewHolder.edit_indicator.setVisibility(View.VISIBLE);
                viewHolder.edit_indicator.setImageResource(darkBackground ? R.drawable.ic_mode_edit_white_18dp : R.drawable.ic_mode_edit_black_18dp);
                viewHolder.edit_indicator.setAlpha(darkBackground ? 0.7f : 0.57f);
            } else {
                viewHolder.edit_indicator.setVisibility(View.GONE);
            }
        }
        final Transferable transferable = message.getTransferable();
        boolean multiReceived = message.getConversation().getMode() == Conversation.MODE_MULTI
                && message.getMergedStatus() <= Message.STATUS_RECEIVED;
        boolean singleReceived = message.getConversation().getMode() == Conversation.MODE_SINGLE
                && message.getMergedStatus() <= Message.STATUS_RECEIVED;


        if (message.isFileOrImage() || transferable != null || MessageUtils.unInitiatedButKnownSize(message)) {
            FileParams params = message.getFileParams();
            filesize = params.size != null ? UIHelper.filesizeToString(params.size) : null;
            if (transferable != null && (transferable.getStatus() == Transferable.STATUS_FAILED || transferable.getStatus() == Transferable.STATUS_CANCELLED)) {
                error = true;
            }
        }
        switch (message.getMergedStatus()) {
            case Message.STATUS_WAITING:
                info = getContext().getString(R.string.waiting);
                break;
            case Message.STATUS_UNSEND:
                if (transferable != null) {
                    info = getContext().getString(R.string.sending_file, transferable.getProgress());
                } else {
                    info = getContext().getString(R.string.sending);
                }
                break;
            case Message.STATUS_OFFERED:
                info = getContext().getString(R.string.offering);
                break;
            case Message.STATUS_SEND_RECEIVED:
            case Message.STATUS_SEND_DISPLAYED:
                viewHolder.indicatorReceived.setImageResource(darkBackground ? R.drawable.ic_done_white_18dp : R.drawable.ic_done_black_18dp);
                viewHolder.indicatorReceived.setAlpha(darkBackground ? 0.7f : 0.57f);
                viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
                break;
            case Message.STATUS_SEND_FAILED:
                final String errorMessage = message.getErrorMessage();
                if (Message.ERROR_MESSAGE_CANCELLED.equals(errorMessage)) {
                    info = getContext().getString(R.string.cancelled);
                } else if (errorMessage != null) {
                    final String[] errorParts = errorMessage.split("\\u001f", 2);
                    if (errorParts.length == 2) {
                        switch (errorParts[0]) {
                            case "file-too-large":
                                info = getContext().getString(R.string.file_too_large);
                                break;
                            default:
                                info = getContext().getString(R.string.send_failed);
                                break;
                        }
                    } else {
                        info = getContext().getString(R.string.send_failed);
                    }
                } else {
                    info = getContext().getString(R.string.send_failed);
                }
                error = true;
                break;
            default:
                if (mForceNames || multiReceived) {
                    showUsername(viewHolder, message);
                } else if (singleReceived) {
                    showUsername(viewHolder, message);
                }
                break;
        }
        if (error && type == SENT) {
            if (darkBackground) {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption_Warning_OnDark);
            } else {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption_Warning);
            }
        } else {
            if (darkBackground) {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption_OnDark);
            } else {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption);
            }
            viewHolder.time.setTextColor(this.getMessageTextColor(darkBackground, false));
        }
        if (message.getEncryption() == Message.ENCRYPTION_NONE) {
            viewHolder.indicator.setVisibility(View.GONE);
        } else {
            boolean verified = false;
            if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                final FingerprintStatus status = message.getConversation()
                        .getAccount().getAxolotlService().getFingerprintTrust(
                                message.getFingerprint());
                if (status != null && status.isVerified()) {
                    verified = true;
                }
            }
            if (verified) {
                viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_verified_user_white_18dp : R.drawable.ic_verified_user_black_18dp);
            } else {
                viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_lock_white_18dp : R.drawable.ic_lock_black_18dp);
            }
            if (darkBackground) {
                viewHolder.indicator.setAlpha(0.7f);
            } else {
                viewHolder.indicator.setAlpha(0.57f);
            }
            viewHolder.indicator.setVisibility(View.VISIBLE);
        }

        final String formattedTime = UIHelper.readableTimeDifferenceFull(getContext(), message.getMergedTimeSent(), allowRelativeTimestamps);
        final String bodyLanguage = message.getBodyLanguage();
        final String bodyLanguageInfo = bodyLanguage == null ? "" : String.format(" %s", bodyLanguage.toUpperCase(Locale.US));
        if (message.getStatus() <= Message.STATUS_RECEIVED) {
            if ((filesize != null) && (info != null)) {
                viewHolder.time.setText(formattedTime + " " + filesize + " \u00B7 " + info + bodyLanguageInfo);
            } else if ((filesize == null) && (info != null)) {
                viewHolder.time.setText(formattedTime + " \u00B7 " + info + bodyLanguageInfo);
            } else if ((filesize != null) && (info == null)) {
                viewHolder.time.setText(formattedTime + " \u00B7 " + filesize + bodyLanguageInfo);
            } else {
                viewHolder.time.setText(formattedTime + bodyLanguageInfo);
            }
        } else {
            if ((filesize != null) && (info != null)) {
                viewHolder.time.setText(filesize + " " + info + bodyLanguageInfo);
            } else if ((filesize == null) && (info != null)) {
                if (error) {
                    viewHolder.time.setText(info + " " + formattedTime + bodyLanguageInfo);
                } else {
                    viewHolder.time.setText(info);
                }
            } else if ((filesize != null) && (info == null)) {
                viewHolder.time.setText(filesize + " " + formattedTime + bodyLanguageInfo);
            } else {
                viewHolder.time.setText(formattedTime + bodyLanguageInfo);
            }
        }
    }

    private void showUsername(ViewHolder viewHolder, Message message) {
        if (message == null || viewHolder == null) {
            return;
        }
        viewHolder.username.setText(UIHelper.getMessageDisplayName(message));
        if (mForceNames || true) {
            viewHolder.username.setVisibility(View.VISIBLE);
        } else {
            viewHolder.username.setVisibility(View.GONE);
        }
    }



    private void displayInfoMessage(ViewHolder viewHolder, CharSequence text, boolean darkBackground) {
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        viewHolder.messageBody.setText(text);
        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Secondary_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Secondary);
        }
        viewHolder.messageBody.setTextIsSelectable(false);
    }

    private void displayEmojiMessage(final ViewHolder viewHolder, final String body, final boolean darkBackground) {
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Emoji_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Emoji);
        }
        Spannable span = new SpannableString(body);
        float size = Emoticons.isEmoji(body) ? 3.0f : 2.0f;
        span.setSpan(new RelativeSizeSpan(size), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        viewHolder.messageBody.setText(span);
    }

    private void applyQuoteSpan(SpannableStringBuilder body, int start, int end, boolean darkBackground, boolean highlightReply, Message message) {
        if (start > 1 && !"\n\n".equals(body.subSequence(start - 2, start).toString())) {
            body.insert(start++, "\n");
            body.setSpan(
                    new DividerSpan(false),
                    start - ("\n".equals(body.subSequence(start - 2, start - 1).toString()) ? 2 : 1),
                    start,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            end++;
        }
        if (end < body.length() - 1 && !"\n\n".equals(body.subSequence(end, end + 2).toString())) {
            body.insert(end, "\n");
            body.setSpan(
                    new DividerSpan(false),
                    end,
                    end + ("\n".equals(body.subSequence(end + 1, end + 2).toString()) ? 2 : 1),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        int color = this.getMessageTextColor(darkBackground, false);

        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        body.setSpan(new QuoteSpan(color, highlightReply && start == 0 ? ContextCompat.getColor(activity, R.color.blue_a100) : -1,  metrics), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (highlightReply && start == 0) {
            body.setSpan(new ReplyClickableSpan(new WeakReference(replyClickListener), new WeakReference(message)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * Applies QuoteSpan to group of lines which starts with > or » characters.
     * Appends likebreaks and applies DividerSpan to them to show a padding between quote and text.
     */
    public void handleTextQuotes(SpannableStringBuilder body, boolean darkBackground, boolean highlightReply, Message message) {
        boolean startsWithQuote = false;
        int quoteDepth = 0;
        while (QuoteHelper.bodyContainsQuoteStart(body) && quoteDepth <= Config.QUOTE_MAX_DEPTH) {
            if (quoteDepth == 0) {
                quoteDepth = 1;
            }
            char previous = '\n';
            int lineStart = -1;
            int lineTextStart = -1;
            int quoteStart = -1;
            for (int i = 0; i <= body.length(); i++) {
                char current = body.length() > i ? body.charAt(i) : '\n';
                if (lineStart == -1) {
                    if (previous == '\n') {
                        if (i < body.length() && QuoteHelper.isPositionQuoteStart(body, i)) {
                            // Line start with quote
                            lineStart = i;
                            if (quoteStart == -1) quoteStart = i;
                            if (i == 0) startsWithQuote = true;
                        } else if (quoteStart >= 0) {
                            // Line start without quote, apply spans there
                            applyQuoteSpan(body, quoteStart, i - 1, darkBackground, quoteDepth == 1 && highlightReply, message);
                            quoteStart = -1;
                        }
                    }
                } else {
                    // Remove extra spaces between > and first character in the line
                    // > character will be removed too
                    if (current != ' ' && lineTextStart == -1) {
                        lineTextStart = i;
                    }
                    if (current == '\n') {
                        body.delete(lineStart, lineTextStart);
                        i -= lineTextStart - lineStart;
                        if (i == lineStart) {
                            // Avoid empty lines because span over empty line can be hidden
                            body.insert(i++, " ");
                        }
                        lineStart = -1;
                        lineTextStart = -1;
                    }
                }
                previous = current;
            }
            if (quoteStart >= 0) {
                // Apply spans to finishing open quote
                applyQuoteSpan(body, quoteStart, body.length(), darkBackground, quoteDepth == 1 && highlightReply, message);
            }
            quoteDepth++;
        }
        if (quoteDepth == 0 && highlightReply) {
            // Quote was not detected, use a reply anyway if provided
            int start = -1;
            int end = -1;
            for (Element el : message.getPayloads()) {
                if ("fallback".equals(el.getName()) && "urn:xmpp:fallback:0".equals(el.getNamespace()) && "urn:xmpp:reply:0".equals(el.getAttribute("for"))) {
                    Element bodyEl = el.findChild("body", "urn:xmpp:fallback:0");
                    if (bodyEl != null) {
                        String startString = bodyEl.getAttribute("start");
                        String endString = bodyEl.getAttribute("end");
                        try {
                            start = Integer.parseInt(startString);
                            end = Integer.parseInt(endString);
                        } catch (final NumberFormatException ignored) {
                        }
                    }
                    break;
                }
            }
            if (start == -1 && end == -1) {
                // Quirk for messages which were saved without the fallback element
                final String quirk = "reply\n";
                body.insert(0, quirk);
                start = 0;
                end = quirk.length();
            } else if (start == -1) {
                start = 0;
            } else if (end == -1 || end >= body.length()) {
                end = body.length();
            }

            applyQuoteSpan(body, start, end, darkBackground, true, message);
        }
    }

    private void displayTextMessage(final ViewHolder viewHolder, final Message message, boolean darkBackground, int type) {
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);

        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1);
        }
        viewHolder.messageBody.setHighlightColor(ContextCompat.getColor(activity, darkBackground
                ? (type == SENT || !mUseGreenBackground ? R.color.black26 : R.color.grey800) : R.color.grey500));
        viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);

        if (message.getBody() != null) {
            final String nick = UIHelper.getMessageDisplayName(message);

            Message replyMessage = message.getReplyMessage();
            Boolean showReplyAsSeparatePart = (replyMessage != null && replyMessage.isFileOrImage()) || message.isFileOrImage();

            SpannableStringBuilder body = message.getBodyForDisplaying(showReplyAsSeparatePart);

            boolean hasMeCommand = message.hasMeCommand();
            if (hasMeCommand) {
                body = body.replace(0, Message.ME_COMMAND.length(), nick + " ");
            }

            if (body.length() > Config.MAX_DISPLAY_MESSAGE_CHARS) {
                body = new SpannableStringBuilder(body, 0, Config.MAX_DISPLAY_MESSAGE_CHARS);
                body.append("\u2026");
            }

            Message.MergeSeparator[] mergeSeparators = body.getSpans(0, body.length(), Message.MergeSeparator.class);
            for (Message.MergeSeparator mergeSeparator : mergeSeparators) {
                int start = body.getSpanStart(mergeSeparator);
                int end = body.getSpanEnd(mergeSeparator);
                body.setSpan(new DividerSpan(true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            for (final android.text.style.QuoteSpan quote : body.getSpans(0, body.length(), android.text.style.QuoteSpan.class)) {
                int start = body.getSpanStart(quote);
                int end = body.getSpanEnd(quote);
                body.removeSpan(quote);
                applyQuoteSpan(body, start, end, darkBackground, message.getReplyMessage() != null, message);
            }

            maybeShowReply(replyMessage, showReplyAsSeparatePart, viewHolder, message, darkBackground);
            handleTextQuotes(body, darkBackground, message.getReplyMessage() != null && !showReplyAsSeparatePart, message);

            if (!message.isPrivateMessage()) {
                if (hasMeCommand) {
                    body.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, nick.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                /*String privateMarker;
                if (message.getStatus() <= Message.STATUS_RECEIVED) {
                    privateMarker = activity.getString(R.string.private_message);
                } else {
                    Jid cp = message.getCounterpart();
                    privateMarker = activity.getString(R.string.private_message_to, Strings.nullToEmpty(cp == null ? null : cp.getResource()));
                }
                body.insert(0, privateMarker);
                int privateMarkerIndex = privateMarker.length();
                if (startsWithQuote) {
                    body.insert(privateMarkerIndex, "\n\n");
                    body.setSpan(new DividerSpan(false), privateMarkerIndex, privateMarkerIndex + 2,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    body.insert(privateMarkerIndex, " ");
                }
                body.setSpan(new ForegroundColorSpan(getMessageTextColor(darkBackground, false)), 0, privateMarkerIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                body.setSpan(new StyleSpan(Typeface.BOLD), 0, privateMarkerIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (hasMeCommand) {
                    body.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), privateMarkerIndex + 1,
                            privateMarkerIndex + 1 + nick.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }*/
            }
            if (message.getConversation().getMode() == Conversation.MODE_MULTI && message.getStatus() == Message.STATUS_RECEIVED) {
                if (message.getConversation() instanceof Conversation) {
                    final Conversation conversation = (Conversation) message.getConversation();
                    Pattern pattern = NotificationService.generateNickHighlightPattern(conversation.getMucOptions().getActualNick());
                    Matcher matcher = pattern.matcher(body);
                    while (matcher.find()) {
                        body.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            Matcher matcher = Emoticons.getEmojiPattern(body).matcher(body);
            while (matcher.find()) {
                if (matcher.start() < matcher.end()) {
                    body.setSpan(new RelativeSizeSpan(1.2f), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            StylingHelper.format(body, viewHolder.messageBody.getCurrentTextColor());
            if (highlightedTerm != null) {
                StylingHelper.highlight(activity, body, highlightedTerm, StylingHelper.isDarkText(viewHolder.messageBody));
            }
            MyLinkify.addLinks(body, true);
            viewHolder.messageBody.setAutoLinkMask(0);
            viewHolder.messageBody.setText(body);
            viewHolder.messageBody.setMovementMethod(ClickableMovementMethod.getInstance());
        } else {
            viewHolder.messageBody.setText("");
            viewHolder.messageBody.setTextIsSelectable(false);
        }
    }

    private void maybeShowReply(Message replyMessage, boolean showAsSeparatePart, ViewHolder viewHolder, Message message, boolean darkBackground) {
        TextView text = viewHolder.nonTextReplyContent.findViewById(R.id.reply_body);
        TextView author = viewHolder.nonTextReplyContent.findViewById(R.id.context_preview_author);
        ImageView contextPreviewImage = viewHolder.nonTextReplyContent.findViewById(R.id.context_preview_image);
        ImageView contextPreviewDoc = viewHolder.nonTextReplyContent.findViewById(R.id.context_preview_doc);
        ImageView contextPreviewAudio = viewHolder.nonTextReplyContent.findViewById(R.id.context_preview_audio);
        View iconsContainer = viewHolder.nonTextReplyContent.findViewById(R.id.icons_container);

        if (showAsSeparatePart && replyMessage != null) {
            viewHolder.nonTextReplyContent.setVisibility(View.VISIBLE);
            WeakReference<ReplyClickListener> listener = new WeakReference<>(replyClickListener);
            viewHolder.nonTextReplyContent.setOnClickListener(v -> {
                ReplyClickListener l = listener.get();
                if (l != null) {
                    l.onReplyClick(message);
                }
            });

            text.setVisibility(View.VISIBLE);

            int color = this.getMessageTextColor(darkBackground, false);
            text.setTextColor(color);
            author.setTextColor(color);
            contextPreviewDoc.setColorFilter(color);
            contextPreviewAudio.setColorFilter(color);

            text.setText(replyMessage.getBodyForReplyPreview(activity.xmppConnectionService));
            author.setText(replyMessage.getAvatarName());

            if (replyMessage.getFileParams().width > 0 && replyMessage.getFileParams().height > 0) {
                iconsContainer.setVisibility(View.VISIBLE);
                contextPreviewImage.setVisibility(View.VISIBLE);
                contextPreviewDoc.setVisibility(View.GONE);
                contextPreviewAudio.setVisibility(View.GONE);
                activity.loadBitmap(replyMessage, contextPreviewImage);
            } else if (replyMessage.getFileParams().runtime > 0) {
                iconsContainer.setVisibility(View.VISIBLE);
                contextPreviewImage.setVisibility(View.GONE);
                contextPreviewDoc.setVisibility(View.GONE);
                contextPreviewAudio.setVisibility(View.VISIBLE);
            } else if (replyMessage.isFileOrImage()) {
                iconsContainer.setVisibility(View.VISIBLE);
                contextPreviewImage.setVisibility(View.GONE);
                contextPreviewDoc.setVisibility(View.VISIBLE);
                contextPreviewAudio.setVisibility(View.GONE);
            } else {
                iconsContainer.setVisibility(View.GONE);
            }
        } else if (replyMessage != null) {
            viewHolder.nonTextReplyContent.setVisibility(View.VISIBLE);
            contextPreviewImage.setVisibility(View.GONE);
            contextPreviewDoc.setVisibility(View.GONE);
            contextPreviewAudio.setVisibility(View.GONE);
            text.setVisibility(View.GONE);
            iconsContainer.setVisibility(View.GONE);

            int color = this.getMessageTextColor(darkBackground, false);
            author.setTextColor(color);
            author.setText(replyMessage.getAvatarName());
        } else {
            viewHolder.nonTextReplyContent.setVisibility(View.GONE);
        }
    }

    private void displayDownloadableMessage(ViewHolder viewHolder, final Message message, String text, final boolean darkBackground) {
        toggleWhisperInfo(viewHolder, message, darkBackground);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(text);
        viewHolder.download_button.setOnClickListener(v -> ConversationFragment.downloadFile(activity, message));

        maybeShowReply(message.getReplyMessage(), true, viewHolder, message, darkBackground);
    }

    private void displayOpenableMessage(ViewHolder viewHolder, final Message message, final boolean darkBackground) {
        toggleWhisperInfo(viewHolder, message, darkBackground);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message)));
        viewHolder.download_button.setOnClickListener(v -> openDownloadable(message));

        maybeShowReply(message.getReplyMessage(), true, viewHolder, message, darkBackground);
    }

    private void displayLocationMessage(ViewHolder viewHolder, final Message message, final boolean darkBackground) {
        toggleWhisperInfo(viewHolder, message, darkBackground);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(R.string.show_location);
        viewHolder.download_button.setOnClickListener(v -> showLocation(message));

        maybeShowReply(message.getReplyMessage(), true, viewHolder, message, darkBackground);
    }

    private void displayAudioMessage(ViewHolder viewHolder, Message message, boolean darkBackground) {
        toggleWhisperInfo(viewHolder, message, darkBackground);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.GONE);
        final RelativeLayout audioPlayer = viewHolder.audioPlayer;
        audioPlayer.setVisibility(View.VISIBLE);
        AudioPlayer.ViewHolder.get(audioPlayer).setDarkBackground(darkBackground);
        this.audioPlayer.init(audioPlayer, message);

        maybeShowReply(message.getReplyMessage(), true, viewHolder, message, darkBackground);
    }

    private void displayMediaPreviewMessage(ViewHolder viewHolder, final Message message, final boolean darkBackground) {
        toggleWhisperInfo(viewHolder, message, darkBackground);
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.VISIBLE);
        maybeShowReply(message.getReplyMessage(), true, viewHolder, message, darkBackground);
        final FileParams params = message.getFileParams();
        final float target = activity.getResources().getDimension(R.dimen.image_preview_width);
        final int scaledW;
        final int scaledH;
        if (Math.max(params.height, params.width) * metrics.density <= target) {
            scaledW = (int) (params.width * metrics.density);
            scaledH = (int) (params.height * metrics.density);
        } else if (Math.max(params.height, params.width) <= target) {
            scaledW = params.width;
            scaledH = params.height;
        } else if (params.width <= params.height) {
            scaledW = (int) (params.width / ((double) params.height / target));
            scaledH = (int) target;
        } else {
            scaledW = (int) target;
            scaledH = (int) (params.height / ((double) params.width / target));
        }
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(scaledW, scaledH);
        layoutParams.setMargins(0, (int) (metrics.density * 4), 0, (int) (metrics.density * 4));
        viewHolder.image.setLayoutParams(layoutParams);
        activity.loadBitmap(message, viewHolder.image);
        viewHolder.image.setOnClickListener(v -> openDownloadable(message));
    }

    private void toggleWhisperInfo(ViewHolder viewHolder, final Message message, final boolean darkBackground) {
        if (false && message.isPrivateMessage()) {
            final String privateMarker;
            if (message.getStatus() <= Message.STATUS_RECEIVED) {
                privateMarker = activity.getString(R.string.private_message);
            } else {
                Jid cp = message.getCounterpart();
                privateMarker = activity.getString(R.string.private_message_to, Strings.nullToEmpty(cp == null ? null : cp.getResource()));
            }
            final SpannableString body = new SpannableString(privateMarker);
            body.setSpan(new ForegroundColorSpan(getMessageTextColor(darkBackground, false)), 0, privateMarker.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            body.setSpan(new StyleSpan(Typeface.BOLD), 0, privateMarker.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            viewHolder.messageBody.setText(body);
            viewHolder.messageBody.setVisibility(View.VISIBLE);
        } else {
            viewHolder.messageBody.setVisibility(View.GONE);
        }
    }

    private void loadMoreMessages(Conversation conversation) {
        conversation.setLastClearHistory(0, null);
        activity.xmppConnectionService.updateConversation(conversation);
        conversation.setHasMessagesLeftOnServer(true);
        conversation.setFirstMamReference(null);
        long timestamp = conversation.getLastMessageTransmitted().getTimestamp();
        if (timestamp == 0) {
            timestamp = System.currentTimeMillis();
        }
        conversation.messagesLoaded.set(true);
        MessageArchiveService.Query query = activity.xmppConnectionService.getMessageArchiveService().query(conversation, new MamReference(0), timestamp, false);
        if (query != null) {
            Toast.makeText(activity, R.string.fetching_history_from_server, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(activity, R.string.not_fetching_history_retention_period, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        final Message message = getItem(position);
        final boolean omemoEncryption = message.getEncryption() == Message.ENCRYPTION_AXOLOTL;
        final boolean isInValidSession = message.isValidInSession() && (!omemoEncryption || message.isTrusted());
        final Conversational conversation = message.getConversation();
        final Account account = conversation.getAccount();
        final int type = getItemViewType(position);

        ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            viewHolder.position = position;
            switch (type) {
                case DATE_SEPARATOR:
                    view = activity.getLayoutInflater().inflate(R.layout.message_date_bubble, parent, false);
                    viewHolder.root = view;
                    viewHolder.status_message = view.findViewById(R.id.message_body);
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.indicatorReceived = view.findViewById(R.id.indicator_received);
                    break;
                case RTP_SESSION:
                    view = activity.getLayoutInflater().inflate(R.layout.message_rtp_session, parent, false);
                    viewHolder.root = view;
                    viewHolder.status_message = view.findViewById(R.id.message_body);
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.indicatorReceived = view.findViewById(R.id.indicator_received);
                    break;
                case SENT:
                    view = activity.getLayoutInflater().inflate(R.layout.message_sent, parent, false);
                    viewHolder.clicksInterceptor = view.findViewById(R.id.clicks_interceptor);
                    viewHolder.root = view;
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    viewHolder.download_button = view.findViewById(R.id.download_button);
                    viewHolder.indicator = view.findViewById(R.id.security_indicator);
                    viewHolder.edit_indicator = view.findViewById(R.id.edit_indicator);
                    viewHolder.image = view.findViewById(R.id.message_image);
                    viewHolder.messageBody = view.findViewById(R.id.message_body);
                    viewHolder.nonTextReplyContent = view.findViewById(R.id.non_text_reply_content);
                    viewHolder.time = view.findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = view.findViewById(R.id.indicator_received);
                    viewHolder.audioPlayer = view.findViewById(R.id.audio_player);
                    view.setTag(R.id.TAG_DRAGGABLE, true);
                    break;
                case RECEIVED:
                    view = activity.getLayoutInflater().inflate(R.layout.message_received, parent, false);
                    viewHolder.clicksInterceptor = view.findViewById(R.id.clicks_interceptor);
                    viewHolder.root = view;
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    viewHolder.download_button = view.findViewById(R.id.download_button);
                    viewHolder.indicator = view.findViewById(R.id.security_indicator);
                    viewHolder.edit_indicator = view.findViewById(R.id.edit_indicator);
                    viewHolder.image = view.findViewById(R.id.message_image);
                    viewHolder.username = view.findViewById(R.id.username);
                    viewHolder.messageBody = view.findViewById(R.id.message_body);
                    viewHolder.nonTextReplyContent = view.findViewById(R.id.non_text_reply_content);
                    viewHolder.time = view.findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = view.findViewById(R.id.indicator_received);
                    viewHolder.encryption = view.findViewById(R.id.message_encryption);
                    viewHolder.audioPlayer = view.findViewById(R.id.audio_player);
                    view.setTag(R.id.TAG_DRAGGABLE, true);
                    break;
                case STATUS:
                    view = activity.getLayoutInflater().inflate(R.layout.message_status, parent, false);
                    viewHolder.root = view;
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    viewHolder.status_message = view.findViewById(R.id.status_message);
                    viewHolder.load_more_messages = view.findViewById(R.id.load_more_messages);
                    break;
                default:
                    throw new AssertionError("Unknown view type");
            }
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
            if (dragHelper != null && dragHelper.getCapturedView() == view) {
                dragHelper.abort();
            }

            if (viewHolder == null) {
                return view;
            } else {
                viewHolder.position = position;
            }
        }

        View highlighter = view.findViewById(R.id.highlighter);
        if (highlighter != null) {
            highlighter.setVisibility(View.INVISIBLE);
        }

        boolean darkBackground = type == RECEIVED && (!isInValidSession || mUseGreenBackground) || activity.isDarkTheme();

        if (type == DATE_SEPARATOR) {
            if (UIHelper.today(message.getTimeSent())) {
                viewHolder.status_message.setText(R.string.today);
            } else if (UIHelper.yesterday(message.getTimeSent())) {
                viewHolder.status_message.setText(R.string.yesterday);
            } else {
                viewHolder.status_message.setText(DateUtils.formatDateTime(activity, message.getTimeSent(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
            }
            viewHolder.message_box.setBackgroundResource(activity.isDarkTheme() ? R.drawable.date_bubble_grey : R.drawable.date_bubble_white);
            return view;
        } else if (type == RTP_SESSION) {
            final boolean isDarkTheme = activity.isDarkTheme();
            final boolean received = message.getStatus() <= Message.STATUS_RECEIVED;
            final RtpSessionStatus rtpSessionStatus = RtpSessionStatus.of(message.getBody());
            final long duration = rtpSessionStatus.duration;
            if (received) {
                if (duration > 0) {
                    viewHolder.status_message.setText(activity.getString(R.string.incoming_call_duration_timestamp, TimeFrameUtils.resolve(activity, duration), UIHelper.readableTimeDifferenceFull(activity, message.getTimeSent(), allowRelativeTimestamps)));
                } else if (rtpSessionStatus.successful) {
                    viewHolder.status_message.setText(R.string.incoming_call);
                } else {
                    viewHolder.status_message.setText(activity.getString(R.string.missed_call_timestamp, UIHelper.readableTimeDifferenceFull(activity, message.getTimeSent(), allowRelativeTimestamps)));
                }
            } else {
                if (duration > 0) {
                    viewHolder.status_message.setText(activity.getString(R.string.outgoing_call_duration_timestamp, TimeFrameUtils.resolve(activity, duration), UIHelper.readableTimeDifferenceFull(activity, message.getTimeSent(), allowRelativeTimestamps)));
                } else {
                    viewHolder.status_message.setText(activity.getString(R.string.outgoing_call_timestamp, UIHelper.readableTimeDifferenceFull(activity, message.getTimeSent(), allowRelativeTimestamps)));
                }
            }
            viewHolder.indicatorReceived.setImageResource(RtpSessionStatus.getDrawable(received, rtpSessionStatus.successful, isDarkTheme));
            viewHolder.indicatorReceived.setAlpha(isDarkTheme ? 0.7f : 0.57f);
            viewHolder.message_box.setBackgroundResource(isDarkTheme ? R.drawable.date_bubble_grey : R.drawable.date_bubble_white);
            return view;
        } else if (type == STATUS) {
            if ("LOAD_MORE".equals(message.getBody())) {
                viewHolder.status_message.setVisibility(View.GONE);
                viewHolder.contact_picture.setVisibility(View.GONE);
                viewHolder.load_more_messages.setVisibility(View.VISIBLE);
                viewHolder.load_more_messages.setOnClickListener(v -> loadMoreMessages((Conversation) message.getConversation()));
            } else {
                viewHolder.status_message.setVisibility(View.VISIBLE);
                viewHolder.load_more_messages.setVisibility(View.GONE);
                viewHolder.status_message.setText(message.getBody());
                boolean showAvatar;
                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    showAvatar = true;
                    AvatarWorkerTask.loadAvatar(message, viewHolder.contact_picture, R.dimen.avatar_on_status_message);
                } else if (message.getCounterpart() != null || message.getTrueCounterpart() != null || (message.getCounterparts() != null && message.getCounterparts().size() > 0)) {
                    showAvatar = true;
                    AvatarWorkerTask.loadAvatar(message, viewHolder.contact_picture, R.dimen.avatar_on_status_message);
                } else {
                    showAvatar = false;
                }
                if (showAvatar) {
                    viewHolder.contact_picture.setAlpha(0.5f);
                    viewHolder.contact_picture.setVisibility(View.VISIBLE);
                } else {
                    viewHolder.contact_picture.setVisibility(View.GONE);
                }
            }
            return view;
        } else {
            AvatarWorkerTask.loadAvatar(message, viewHolder.contact_picture, R.dimen.avatar);
        }

        resetClickListener(viewHolder.message_box, viewHolder.messageBody);

        View.OnLongClickListener messageItemLongClickListener = v -> {
            if (messageEmptyPartClickListener != null) {
                messageEmptyPartClickListener.onMessageEmptyPartLongClick(message);
            }

            return messageEmptyPartClickListener != null;
        };

        View.OnClickListener messageItemClickListener = v -> {
            if (messageEmptyPartClickListener != null) {
                messageEmptyPartClickListener.onMessageEmptyPartClick(message);
            }
        };

        viewHolder.root.setOnLongClickListener(messageItemLongClickListener);
        viewHolder.root.setOnClickListener(messageItemClickListener);

        viewHolder.clicksInterceptor.setOnClickListener(messageItemClickListener);
        viewHolder.clicksInterceptor.setOnLongClickListener(messageItemLongClickListener);

        viewHolder.contact_picture.setOnClickListener(v -> {
            if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
                MessageAdapter.this.mOnContactPictureClickedListener
                        .onContactPictureClicked(message);
            }

        });
        viewHolder.contact_picture.setOnLongClickListener(v -> {
            if (MessageAdapter.this.mOnContactPictureLongClickedListener != null) {
                MessageAdapter.this.mOnContactPictureLongClickedListener
                        .onContactPictureLongClicked(v, message);
                return true;
            } else {
                return false;
            }
        });

        viewHolder.clicksInterceptor.setVisibility(
                (selectionStatusProvider != null && selectionStatusProvider.isSomethingSelected())
                        ? View.VISIBLE : View.GONE);
        if (selectionStatusProvider == null || !selectionStatusProvider.isSelected(message)) {
            viewHolder.root.setBackground(null);
        } else {
            viewHolder.root.setBackgroundColor(StyledAttributes.getColor(view.getContext(), R.attr.color_message_selection));
        }

        final Transferable transferable = message.getTransferable();
        final boolean unInitiatedButKnownSize = MessageUtils.unInitiatedButKnownSize(message);
        if (unInitiatedButKnownSize || message.isDeleted() || (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING)) {
            if (unInitiatedButKnownSize || transferable != null && transferable.getStatus() == Transferable.STATUS_OFFER) {
                displayDownloadableMessage(viewHolder, message, activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, message)), darkBackground);
            } else if (transferable != null && transferable.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE) {
                displayDownloadableMessage(viewHolder, message, activity.getString(R.string.check_x_filesize, UIHelper.getFileDescriptionString(activity, message)), darkBackground);
            } else {
                displayInfoMessage(viewHolder, UIHelper.getMessagePreview(activity, message).first, darkBackground);
            }
        } else if (message.isFileOrImage() && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
            if (message.getFileParams().width > 0 && message.getFileParams().height > 0) {
                displayMediaPreviewMessage(viewHolder, message, darkBackground);
            } else if (message.getFileParams().runtime > 0) {
                displayAudioMessage(viewHolder, message, darkBackground);
            } else {
                displayOpenableMessage(viewHolder, message, darkBackground);
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            if (account.isPgpDecryptionServiceConnected()) {
                if (conversation instanceof Conversation && !account.hasPendingPgpIntent((Conversation) conversation)) {
                    displayInfoMessage(viewHolder, activity.getString(R.string.message_decrypting), darkBackground);
                } else {
                    displayInfoMessage(viewHolder, activity.getString(R.string.pgp_message), darkBackground);
                }
            } else {
                displayInfoMessage(viewHolder, activity.getString(R.string.install_openkeychain), darkBackground);
                viewHolder.message_box.setOnClickListener(this::promptOpenKeychainInstall);
                viewHolder.messageBody.setOnClickListener(this::promptOpenKeychainInstall);
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
            displayInfoMessage(viewHolder, activity.getString(R.string.decryption_failed), darkBackground);
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE) {
            displayInfoMessage(viewHolder, activity.getString(R.string.not_encrypted_for_this_device), darkBackground);
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
            displayInfoMessage(viewHolder, activity.getString(R.string.omemo_decryption_failed), darkBackground);
        } else {
            if (message.isGeoUri()) {
                displayLocationMessage(viewHolder, message, darkBackground);
            } else if (message.bodyIsOnlyEmojis() && message.getType() != Message.TYPE_PRIVATE) {
                displayEmojiMessage(viewHolder, message.getBody().trim(), darkBackground);
            } else if (message.treatAsDownloadable()) {
                try {
                    final URI uri = new URI(message.getBody());
                    displayDownloadableMessage(viewHolder,
                            message,
                            activity.getString(R.string.check_x_filesize_on_host,
                                    UIHelper.getFileDescriptionString(activity, message),
                                    uri.getHost()),
                            darkBackground);
                } catch (Exception e) {
                    displayDownloadableMessage(viewHolder,
                            message,
                            activity.getString(R.string.check_x_filesize,
                                    UIHelper.getFileDescriptionString(activity, message)),
                            darkBackground);
                }
            } else {
                displayTextMessage(viewHolder, message, darkBackground, type);
            }
        }

        boolean mergeableWithPrev = message.mergeable(message.prev());
        boolean mergeableWithNext = message.mergeable(message.next());

        if (type == RECEIVED) {
            if (isInValidSession) {
                int bubble;
                if (!mUseGreenBackground) {
                    if (mergeableWithNext) {
                        bubble = activity.getThemeResource(R.attr.message_bubble_received_monochrome_non_last, R.drawable.message_bubble_received_white_non_last);
                    } else {
                        bubble = activity.getThemeResource(R.attr.message_bubble_received_monochrome, R.drawable.message_bubble_received_white);
                    }

                    viewHolder.message_box.setBackgroundResource(bubble);
                } else {
                    if (mergeableWithNext) {
                        bubble = activity.getThemeResource(R.attr.message_bubble_received_green_non_last, R.drawable.message_bubble_received_non_last);
                    } else {
                        bubble = activity.getThemeResource(R.attr.message_bubble_received_green, R.drawable.message_bubble_received);
                    }

                    Drawable bubbleDrawable = AppCompatResources.getDrawable(getContext(), bubble);
                    bubbleDrawable.setTint(getOrCalculatePrimaryColor());
                    viewHolder.message_box.setBackground(bubbleDrawable);
                }

                viewHolder.encryption.setVisibility(View.GONE);
            } else {
                if (mergeableWithNext) {
                    viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_warning_non_last);
                } else {
                    viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_warning);
                }
                viewHolder.encryption.setVisibility(View.VISIBLE);
                if (omemoEncryption && !message.isTrusted()) {
                    viewHolder.encryption.setText(R.string.not_trusted);
                } else {
                    viewHolder.encryption.setText(CryptoHelper.encryptionTypeToText(message.getEncryption()));
                }
            }
        }

        if (type == SENT) {
            int bubble;
            if (mergeableWithNext) {
                bubble = activity.getThemeResource(R.attr.message_bubble_sent_non_last, R.drawable.message_bubble_sent_non_last);
            } else {
                bubble = activity.getThemeResource(R.attr.message_bubble_sent, R.drawable.message_bubble_sent);
            }

            viewHolder.message_box.setBackgroundResource(bubble);
        }

        displayStatus(viewHolder, message, type, darkBackground);

        if (viewHolder.contact_picture != null)
            viewHolder.contact_picture.setVisibility(mergeableWithNext ? View.INVISIBLE : View.VISIBLE);
       // if (viewHolder.edit_indicator != null)
        //    viewHolder.edit_indicator.setVisibility(mergeableWithNext ? View.GONE : viewHolder.edit_indicator.getVisibility());
        // if (viewHolder.encryption != null)
        //    viewHolder.encryption.setVisibility(mergeableWithNext ? View.GONE : viewHolder.encryption.getVisibility());
       // if (viewHolder.indicator != null)
       //     viewHolder.indicator.setVisibility(mergeableWithNext ? View.GONE : viewHolder.indicator.getVisibility());
       // if (viewHolder.time != null)
       //     viewHolder.time.setVisibility(mergeableWithNext ? View.GONE : View.VISIBLE);
       // if (viewHolder.indicatorReceived != null)
         //   viewHolder.indicatorReceived.setVisibility(mergeableWithNext ? View.GONE : viewHolder.indicatorReceived.getVisibility());

        if (mergeableWithPrev && mergeableWithNext) {
            view.setPadding(view.getPaddingLeft(), dpToPx(4), view.getPaddingRight(), dpToPx(4));
        } else if (mergeableWithPrev) {
            view.setPadding(view.getPaddingLeft(), dpToPx(4), view.getPaddingRight(), dpToPx(4));
        } else if (mergeableWithNext) {
            view.setPadding(view.getPaddingLeft(), dpToPx(4), view.getPaddingRight(), dpToPx(4));
        } else {
            view.setPadding(view.getPaddingLeft(), dpToPx(4), view.getPaddingRight(), dpToPx(4));
        }
        viewHolder.contact_picture.setVisibility(View.VISIBLE);
        return view;
    }

    private static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    private void promptOpenKeychainInstall(View view) {
        activity.showInstallPgpDialog();
    }

    public FileBackend getFileBackend() {
        return activity.xmppConnectionService.getFileBackend();
    }

    public void stopAudioPlayer() {
        audioPlayer.stop();
    }

    public void unregisterListenerInAudioPlayer() {
        audioPlayer.unregisterListener();
    }

    public void startStopPending() {
        audioPlayer.startStopPending();
    }

    public void openDownloadable(Message message) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ConversationFragment.registerPendingMessage(activity, message);
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, ConversationsActivity.REQUEST_OPEN_MESSAGE);
            return;
        }
        final DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        ViewUtil.view(activity, file);
    }

    private void showLocation(Message message) {
        for (Intent intent : GeoHelper.createGeoIntentsFromMessage(activity, message)) {
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(intent);
                return;
            }
        }
        Toast.makeText(activity, R.string.no_application_found_to_display_location, Toast.LENGTH_SHORT).show();
    }

    @ColorInt
    private int getOrCalculatePrimaryColor() {
        if (primaryColor != -1) return primaryColor;

        TypedValue typedValue = new TypedValue();
        getContext().getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
        primaryColor = typedValue.data;

        return primaryColor;
    }

    public void updatePreferences() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        this.mUseGreenBackground = p.getBoolean("use_green_background", activity.getResources().getBoolean(R.bool.use_green_background));
    }


    public void setHighlightedTerm(List<String> terms) {
        this.highlightedTerm = terms == null ? null : StylingHelper.filterHighlightedWords(terms);
    }

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(View v, Message message);
    }

    public interface MessageEmptyPartClickListener {
        void onMessageEmptyPartClick(Message message);
        void onMessageEmptyPartLongClick(Message message);
    }

    public interface SelectionStatusProvider {
        boolean isSelected(Message message);
        boolean isSomethingSelected();
    }

    public interface MessageBoxSwipedListener {
        void onMessageBoxReleasedAfterSwipe(Message message);
        void onMessageBoxSwipedEnough();
    }

    public interface ReplyClickListener {
        void onReplyClick(Message message);
    }

    private static class ReplyClickableSpan extends ClickableSpan {
        private WeakReference<ReplyClickListener> replyClickListener;
        private WeakReference<Message> message;

        public ReplyClickableSpan(WeakReference<ReplyClickListener> replyClickListener, WeakReference<Message> message) {
            this.replyClickListener = replyClickListener;
            this.message = message;
        }

        @Override
        public void onClick(@NonNull View widget) {
            ReplyClickListener listener = replyClickListener.get();
            Message message = this.message.get();
            if (listener != null && message != null) {
                listener.onReplyClick(message);
            }
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            ds.setUnderlineText(false);
        }
    }

    private static class ViewHolder {
        public View root;

        public Button load_more_messages;
        public ImageView edit_indicator;
        public RelativeLayout audioPlayer;
        protected LinearLayout message_box;
        protected Button download_button;
        protected ImageView image;
        protected ImageView indicator;
        protected ImageView indicatorReceived;
        protected TextView time;
        protected TextView username;
        protected TextView messageBody;
        protected ImageView contact_picture;
        protected TextView status_message;
        protected TextView encryption;

        protected View nonTextReplyContent;

        protected View clicksInterceptor;

        int position;
    }
}
