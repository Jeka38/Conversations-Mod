package eu.siacs.conversations.ui.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ConversationListRowBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.OngoingRtpSession;

public class ConversationAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ACCOUNT = 0;
    private static final int VIEW_TYPE_TAG = 1;
    private static final int VIEW_TYPE_CONVERSATION = 2;

    private static final String EXPANDED_ACCOUNTS_KEY = "expandedAccounts";

    private static final String EXPANDED_TAG_KEY_PREFIX = "expandedTags_";

    private final XmppActivity activity;
    private final List<Conversation> conversations;
    private OnConversationClickListener listener;

    private boolean allowRelativeTimestamps;

    private ListItem.Tag generalTag;

    private List<Object> items = new ArrayList<>();
    private Map<Account, Set<String>> expandedItems = new HashMap<>();
    private boolean expandedItemsRestored = false;

    private Map<Account, Map<ListItem.Tag, Set<Conversation>>> groupedItems = new HashMap<>();

    private boolean groupingEnabled = false;

    SharedPreferences prefs;

    public ConversationAdapter(XmppActivity activity, List<Conversation> conversations, List<ListItem.Tag> tags) {
        this.activity = activity;
        this.conversations = conversations;

        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        allowRelativeTimestamps = !p.getBoolean("always_full_timestamps", activity.getResources().getBoolean(R.bool.always_full_timestamps));

        String generalTagName = activity.getString(R.string.contact_tag_general);
        generalTag = new ListItem.Tag(generalTagName, UIHelper.getColorForName(generalTagName, true));

        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                if (groupingEnabled) {
                    items.clear();
                    groupedItems.clear();

                    List<Account> accounts = activity.xmppConnectionService.getAccounts();


                    if (!expandedItemsRestored) {
                        prefs = activity.getSharedPreferences("expansionPrefs", Context.MODE_PRIVATE);
                        Set<String> expandedAccounts = new HashSet<>(prefs.getStringSet(EXPANDED_ACCOUNTS_KEY, Collections.emptySet()));

                        if (accounts.size() == 1) {
                            expandedAccounts.add(accounts.get(0).getUuid());
                        }

                        for (String id : expandedAccounts) {
                            Set<String> expandedTags = new HashSet<>(prefs.getStringSet(EXPANDED_TAG_KEY_PREFIX + id, Collections.emptySet()));
                            Account account = activity.xmppConnectionService.findAccountByUuid(id);
                            expandedItems.put(account, expandedTags);
                        }

                        expandedItemsRestored = true;
                    }

                    for (Account account : accounts) {
                        if (accounts.size() > 1) {
                            items.add(account);
                        }

                        boolean accountExpanded = accounts.size() == 1 || expandedItems.containsKey(account);

                        boolean generalTagAdded = false;
                        int initialPosition = items.size();

                        Set<String> expandedTags = expandedItems.getOrDefault(account, Collections.emptySet());

                        Map<ListItem.Tag, Set<Conversation>> groupedItems = new HashMap<>();

                        Map<Conversation, List<ListItem.Tag>> tagsToConversationCache = new HashMap<>();

                        for (int i = 0; i < conversations.size(); i++) {
                            Conversation item = conversations.get(i);

                            if (item.getAccount() != account) continue;

                            List<ListItem.Tag> itemTags = item.getContact().getTags(activity);

                            if (item.getBookmark() != null) {
                                itemTags.addAll(item.getBookmark().getTags(activity));
                            }

                            tagsToConversationCache.put(item, itemTags);

                            if (itemTags.size() == 0 || (itemTags.size() == 1 && UIHelper.isStatusTag(activity, itemTags.get(0)))) {
                                if (accountExpanded && !generalTagAdded) {
                                    items.add(initialPosition, generalTag);
                                    generalTagAdded = true;
                                }

                                if (accountExpanded && expandedTags.contains(generalTag.getName().toLowerCase(Locale.US))) {
                                    items.add(item);
                                }

                                Set<Conversation> group = groupedItems.computeIfAbsent(generalTag, t -> new HashSet<>());
                                group.add(item);
                            }
                        }

                        for (ListItem.Tag tag : tags) {
                            if (UIHelper.isStatusTag(activity, tag)) {
                                continue;
                            }

                            if (accountExpanded) {
                                items.add(tag);
                            }

                            for (int i = 0; i < conversations.size(); i++) {
                                Conversation item = conversations.get(i);

                                if (item.getAccount() != account) continue;

                                List<ListItem.Tag> itemTags = tagsToConversationCache.get(item);

                                if (itemTags.contains(tag)) {
                                    if (accountExpanded && expandedTags.contains(tag.getName().toLowerCase(Locale.US))) {
                                        items.add(item);
                                    }

                                    Set<Conversation> group = groupedItems.computeIfAbsent(tag, t -> new HashSet<>());

                                    group.add(item);
                                }
                            }

                            if (accountExpanded && groupedItems.get(tag) == null) {
                                items.remove(items.size() - 1);
                            }
                        }

                        ConversationAdapter.this.groupedItems.put(account, groupedItems);
                    }
                }
            }
        });
    }

    public boolean isGroupingEnabled() {
        return groupingEnabled;
    }

    @Nullable
    public Conversation getConversation(int position) {
        if (groupingEnabled) {
           Object item = items.get(position);
           if (item instanceof Conversation) {
               return (Conversation) item;
           } else {
               return null;
           }
        } else {
           return conversations.get(position);
        }
    }

    public void setGroupingEnabled(boolean groupingEnabled) {
        if (groupingEnabled != this.groupingEnabled) {
            this.groupingEnabled = groupingEnabled;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (!groupingEnabled) {
            return VIEW_TYPE_CONVERSATION;
        } else {
            Object item = items.get(position);

            if (item instanceof Account) {
                return VIEW_TYPE_ACCOUNT;
            } else if (item instanceof ListItem.Tag) {
                return VIEW_TYPE_TAG;
            } else {
                return VIEW_TYPE_CONVERSATION;
            }
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ACCOUNT) {
            return new AccountViewHolder(parent);
        } else if (viewType == VIEW_TYPE_TAG) {
            return new TagViewHolder(parent);
        } else {
            return new ConversationViewHolder(
                    DataBindingUtil.inflate(
                            LayoutInflater.from(parent.getContext()),
                            R.layout.conversation_list_row,
                            parent,
                            false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        if (groupingEnabled) {
            if (viewHolder instanceof ConversationViewHolder) {
                bindConversation((ConversationViewHolder) viewHolder, (Conversation) items.get(position));
            } else if (viewHolder instanceof TagViewHolder) {
                bindTag((TagViewHolder) viewHolder, (ListItem.Tag) items.get(position), position);
            } else {
                bindAccount((AccountViewHolder) viewHolder, (Account) items.get(position));
            }
        } else {
            bindConversation((ConversationViewHolder) viewHolder, (Conversation) conversations.get(position));
        }
    }

    @Override
    public int getItemCount() {
        if (groupingEnabled) {
            return items.size();
        } else {
            return conversations.size();
        }
    }

    private void bindAccount(AccountViewHolder viewHolder, Account account) {
        viewHolder.text.setText(activity.getString(R.string.contact_tag_with_total, account.getJid().asBareJid().toString(), getChildCount(account, null)));

        if (account.isOnlineAndConnected()) {
            viewHolder.itemView.setBackgroundColor(StyledAttributes.getColor(activity, R.attr.TextColorOnline));
        } else {
            viewHolder.itemView.setBackgroundColor(StyledAttributes.getColor(activity, android.R.attr.textColorSecondary));
        }

        viewHolder.arrow.setRotation(expandedItems.containsKey(account) ? 180 : 0);

        viewHolder.itemView.setOnClickListener(v -> {
            if (expandedItems.containsKey(account)) {
                expandedItems.remove(account);
            } else {
                expandedItems.put(account, new HashSet<>());
            }

            Set<String> expandedAccounts = new HashSet<>();

            for (Account a : expandedItems.keySet()) {
                expandedAccounts.add(a.getUuid());
            }


            prefs.edit().putStringSet(EXPANDED_ACCOUNTS_KEY, expandedAccounts).apply();

            notifyDataSetChanged();
        });
    }

    private void bindTag(TagViewHolder viewHolder, ListItem.Tag tag, int position) {
        Account account = findAccountForTag(position);
        viewHolder.text.setText(activity.getString(R.string.contact_tag_with_total, tag.getName(), getChildCount(account, tag)));
        viewHolder.text.setBackgroundColor(tag.getColor());

        viewHolder.arrow.setRotation(expandedItems.computeIfAbsent(account, a -> new HashSet<>()).contains(tag.getName().toLowerCase(Locale.US)) ? 180 : 0);

        viewHolder.itemView.setOnClickListener(v -> {
           Set<String> expandedTags = expandedItems.computeIfAbsent(account, a -> new HashSet<>());
           if (expandedTags.contains(tag.getName().toLowerCase(Locale.US))) {
               expandedTags.remove(tag.getName().toLowerCase(Locale.US));
           } else {
               expandedTags.add(tag.getName().toLowerCase(Locale.US));
           }

            prefs.edit().putStringSet(EXPANDED_TAG_KEY_PREFIX + account.getUuid(), expandedItems.get(account)).apply();

           notifyDataSetChanged();
        });
    }

    private void bindConversation(ConversationViewHolder viewHolder, Conversation conversation) {
        if (conversation == null) {
            return;
        }

        CharSequence name = conversation.getName();
        if (conversation.getNextCounterpart() != null) {
            name = viewHolder.binding.getRoot().getResources().getString(R.string.muc_private_conversation_title, conversation.getNextCounterpart().getResource(), conversation.getName());
        }

        if  (conversation.withSelf()) {
            name = viewHolder.binding.getRoot().getResources().getString(R.string.note_to_self_conversation_title, name);
        }

        if (name instanceof Jid) {
            viewHolder.binding.conversationName.setText(
                    IrregularUnicodeDetector.style(activity, (Jid) name));
        } else {
            viewHolder.binding.conversationName.setText(name);
        }

        if (conversation == ConversationFragment.getConversation(activity)) {
            viewHolder.binding.frame.setBackgroundColor(
                    StyledAttributes.getColor(activity, R.attr.color_background_tertiary));
        } else {
            viewHolder.binding.frame.setBackgroundColor(
                    StyledAttributes.getColor(activity, R.attr.color_background_primary));
        }

        Message message = conversation.getLatestMessage();
        final int unreadCount = conversation.unreadCount();
        final boolean isRead = conversation.isRead();
        final Conversation.Draft draft = isRead ? conversation.getDraft() : null;
        if (unreadCount > 0) {
            viewHolder.binding.unreadCount.setVisibility(View.VISIBLE);
            viewHolder.binding.unreadCount.setUnreadCount(unreadCount);
        } else {
            viewHolder.binding.unreadCount.setVisibility(View.GONE);
        }

        if (viewHolder.binding.conversationName.getTag() == null) {
            viewHolder.binding.conversationName.setTag(viewHolder.binding.conversationName.getTypeface());
        }

        if (isRead) {
            viewHolder.binding.conversationName.setTypeface((Typeface) viewHolder.binding.conversationName.getTag(), Typeface.NORMAL);
        } else {
            viewHolder.binding.conversationName.setTypeface(null, Typeface.BOLD);
        }

        if (conversation.getMode() == Conversation.MODE_MULTI) {
            int drId = activity.getThemeResource(R.attr.ic_group_16, R.drawable.ic_group_selected_black_16);
            Drawable dr = AppCompatResources.getDrawable(activity, drId);
            viewHolder.binding.conversationName.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, dr, null);
        } else {
            viewHolder.binding.conversationName.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
        }

        Contact contact = conversation.getContact();

        if (contact != null) {
            viewHolder.binding.presenceIndicator.setStatus(contact.getShownStatus());
        } else {
            viewHolder.binding.presenceIndicator.setStatus(null);
        }

        Account account = conversation.getAccount();

        if (account != null && activity.xmppConnectionService.getAccounts().size() > 1) {
            viewHolder.binding.accountIndicator.setBackgroundColor(UIHelper.getColorForName(account.getJid().asBareJid().getEscapedLocal()));
        } else {
            viewHolder.binding.accountIndicator.setBackgroundColor(Color.TRANSPARENT);
        }

        if (draft != null) {
            viewHolder.binding.conversationLastmsgImg.setVisibility(View.GONE);
            viewHolder.binding.conversationLastmsg.setText(draft.getMessage());
            viewHolder.binding.senderName.setText(R.string.draft);
            viewHolder.binding.senderName.setVisibility(View.VISIBLE);
            viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.NORMAL);
            viewHolder.binding.senderName.setTypeface(null, Typeface.ITALIC);
        } else {
            final boolean fileAvailable = !message.isDeleted();
            final boolean showPreviewText;
            if (fileAvailable
                    && (message.isFileOrImage()
                    || message.treatAsDownloadable()
                    || message.isGeoUri())) {
                final int imageResource;
                if (message.isGeoUri()) {
                    imageResource =
                            activity.getThemeResource(
                                    R.attr.ic_attach_location, R.drawable.ic_attach_location);
                    showPreviewText = false;
                } else {
                    // TODO move this into static MediaPreview method and use same icons as in
                    // MediaAdapter
                    final String mime = message.getMimeType();
                    if (MimeUtils.AMBIGUOUS_CONTAINER_FORMATS.contains(mime)) {
                        final Message.FileParams fileParams = message.getFileParams();
                        if (fileParams.width > 0 && fileParams.height > 0) {
                            imageResource =
                                    activity.getThemeResource(
                                            R.attr.ic_attach_videocam,
                                            R.drawable.ic_attach_videocam);
                            showPreviewText = false;
                        } else if (fileParams.runtime > 0) {
                            imageResource =
                                    activity.getThemeResource(
                                            R.attr.ic_attach_record, R.drawable.ic_attach_record);
                            showPreviewText = false;
                        } else {
                            imageResource =
                                    activity.getThemeResource(
                                            R.attr.ic_attach_document,
                                            R.drawable.ic_attach_document);
                            showPreviewText = true;
                        }
                    } else {
                        switch (Strings.nullToEmpty(mime).split("/")[0]) {
                            case "image":
                                imageResource =
                                        activity.getThemeResource(
                                                R.attr.ic_attach_photo, R.drawable.ic_attach_photo);
                                showPreviewText = false;
                                break;
                            case "video":
                                imageResource =
                                        activity.getThemeResource(
                                                R.attr.ic_attach_videocam,
                                                R.drawable.ic_attach_videocam);
                                showPreviewText = false;
                                break;
                            case "audio":
                                imageResource =
                                        activity.getThemeResource(
                                                R.attr.ic_attach_record,
                                                R.drawable.ic_attach_record);
                                showPreviewText = false;
                                break;
                            default:
                                imageResource =
                                        activity.getThemeResource(
                                                R.attr.ic_attach_document,
                                                R.drawable.ic_attach_document);
                                showPreviewText = true;
                                break;
                        }
                    }
                }
                viewHolder.binding.conversationLastmsgImg.setImageResource(imageResource);
                viewHolder.binding.conversationLastmsgImg.setVisibility(View.VISIBLE);
            } else {
                viewHolder.binding.conversationLastmsgImg.setVisibility(View.GONE);
                showPreviewText = true;
            }
            final Pair<CharSequence, Boolean> preview =
                    UIHelper.getMessagePreview(
                            activity,
                            message,
                            viewHolder.binding.conversationLastmsg.getCurrentTextColor());
            if (showPreviewText) {
                viewHolder.binding.conversationLastmsg.setText(UIHelper.shorten(preview.first));
            } else {
                viewHolder.binding.conversationLastmsgImg.setContentDescription(preview.first);
            }
            viewHolder.binding.conversationLastmsg.setVisibility(
                    showPreviewText ? View.VISIBLE : View.GONE);
            if (preview.second) {
                if (isRead) {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.ITALIC);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.NORMAL);
                } else {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD_ITALIC);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.BOLD);
                }
            } else {
                if (isRead) {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.NORMAL);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.NORMAL);
                } else {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.BOLD);
                }
            }
            if (message.getStatus() == Message.STATUS_RECEIVED) {
                if (conversation.getMode() == Conversation.MODE_MULTI) {
                    viewHolder.binding.senderName.setVisibility(View.VISIBLE);
                    viewHolder.binding.senderName.setText(
                            UIHelper.getMessageDisplayName(message).split("\\s+")[0] + ':');
                } else {
                    viewHolder.binding.senderName.setVisibility(View.GONE);
                }
            } else if (message.getType() != Message.TYPE_STATUS) {
                viewHolder.binding.senderName.setVisibility(View.VISIBLE);
                viewHolder.binding.senderName.setText(activity.getString(R.string.me) + ':');
            } else {
                viewHolder.binding.senderName.setVisibility(View.GONE);
            }
        }

        final Optional<OngoingRtpSession> ongoingCall;
        if (conversation.getMode() == Conversational.MODE_MULTI) {
            ongoingCall = Optional.absent();
        } else {
            ongoingCall =
                    activity.xmppConnectionService
                            .getJingleConnectionManager()
                            .getOngoingRtpConnection(conversation.getContact());
        }

        if (ongoingCall.isPresent()) {
            viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
            final int ic_ongoing_call =
                    activity.getThemeResource(
                            R.attr.ic_ongoing_call_hint, R.drawable.ic_phone_in_talk_black_18dp);
            viewHolder.binding.notificationStatus.setImageResource(ic_ongoing_call);
        } else {
            final long muted_till =
                    conversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
            if (muted_till == Long.MAX_VALUE) {
                viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
                int ic_notifications_off =
                        activity.getThemeResource(
                                R.attr.icon_notifications_off,
                                R.drawable.ic_notifications_off_black_24dp);
                viewHolder.binding.notificationStatus.setImageResource(ic_notifications_off);
            } else if (muted_till >= System.currentTimeMillis()) {
                viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
                int ic_notifications_paused =
                        activity.getThemeResource(
                                R.attr.icon_notifications_paused,
                                R.drawable.ic_notifications_paused_black_24dp);
                viewHolder.binding.notificationStatus.setImageResource(ic_notifications_paused);
            } else if (conversation.alwaysNotify()) {
                viewHolder.binding.notificationStatus.setVisibility(View.GONE);
            } else {
                viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
                int ic_notifications_none =
                        activity.getThemeResource(
                                R.attr.icon_notifications_none,
                                R.drawable.ic_notifications_none_black_24dp);
                viewHolder.binding.notificationStatus.setImageResource(ic_notifications_none);
            }
        }

        long timestamp;
        if (draft != null) {
            timestamp = draft.getTimestamp();
        } else {
            timestamp = conversation.getLatestMessage().getTimeSent();
        }
        viewHolder.binding.pinnedOnTop.setVisibility(
                conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false)
                        ? View.VISIBLE
                        : View.GONE);
        viewHolder.binding.conversationLastupdate.setText(
                UIHelper.readableTimeDifference(activity, timestamp, allowRelativeTimestamps));
        AvatarWorkerTask.loadAvatar(
                conversation,
                viewHolder.binding.conversationImage,
                R.dimen.avatar_on_conversation_overview);
        viewHolder.itemView.setOnClickListener(v -> listener.onConversationClick(v, conversation));
    }

    public void setConversationClickListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void insert(Conversation c, int position) {
        conversations.add(position, c);
        notifyDataSetChanged();
    }

    public void remove(Conversation conversation, int position) {
        conversations.remove(conversation);
        notifyItemRemoved(position);
    }

    @Nullable
    private Account findAccountForTag(int position) {
        Account account = null;

        if (activity.xmppConnectionService.getAccounts().size() == 1) {
            return activity.xmppConnectionService.getAccounts().get(0);
        }

        for (int i = position; i >= 0; i--) {
            Object prev = items.get(i);
            if (prev instanceof Account) {
                account = (Account) prev;
                break;
            }
        }

        return account;
    }

    private int getChildCount(Account account, @Nullable ListItem.Tag tag) {
        if (tag == null) {
            int res = 0;

            for (Conversation c : conversations) {
                if (c.getAccount() == account) {
                    res++;
                }
            }

            return res;
        } else {
            if (account == null) {
                return 0;
            }

            Map<ListItem.Tag, Set<Conversation>> childTags = groupedItems.get(account);
            if (childTags == null) {
                return 0;
            }

            Set<Conversation> childConversations = childTags.get(tag);
            if (childConversations == null) {
                return 0;
            }

            return childConversations.size();
        }
    }

    public interface OnConversationClickListener {
        void onConversationClick(View view, Conversation conversation);
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final ConversationListRowBinding binding;

        private ConversationViewHolder(ConversationListRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setLongClickable(true);
        }
    }

    static class AccountViewHolder extends RecyclerView.ViewHolder {
        private TextView text;
        private View arrow;

        private AccountViewHolder(ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_account, parent, false));
            text = itemView.findViewById(R.id.text);
            arrow = itemView.findViewById(R.id.arrow);
        }
    }

    static class TagViewHolder extends RecyclerView.ViewHolder {
        private TextView text;
        private View arrow;

        private TagViewHolder(ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_group, parent, false));
            text = itemView.findViewById(R.id.text);
            arrow = itemView.findViewById(R.id.arrow);
        }
    }
}
