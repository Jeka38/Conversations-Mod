/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.ui.util;

import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;

public class ShareUtil {

	public static void share(XmppActivity activity, Message message) {
		Intent shareIntent = new Intent();
		shareIntent.setAction(Intent.ACTION_SEND);
		if (message.isGeoUri()) {
			shareIntent.putExtra(Intent.EXTRA_TEXT, message.getBody());
			shareIntent.setType("text/plain");
		} else if (!message.isFileOrImage()) {
			shareIntent.putExtra(Intent.EXTRA_TEXT, message.getBodyForDisplaying().toString());
			shareIntent.setType("text/plain");
			shareIntent.putExtra(ConversationsActivity.EXTRA_AS_QUOTE, message.getStatus() == Message.STATUS_RECEIVED);
		} else {
			final DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
			try {
				shareIntent.putExtra(Intent.EXTRA_STREAM, FileBackend.getUriForFile(activity, file));
			} catch (SecurityException e) {
				Toast.makeText(activity, activity.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();
				return;
			}
			shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			String mime = message.getMimeType();
			if (mime == null) {
				mime = "*/*";
			}
			shareIntent.setType(mime);
		}
		try {
			activity.startActivity(Intent.createChooser(shareIntent, activity.getText(R.string.share_with)));
		} catch (ActivityNotFoundException e) {
			//This should happen only on faulty androids because normally chooser is always available
			Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
		}
	}

	public static void share(XmppActivity activity, String text) {
		Intent shareIntent = new Intent();
		shareIntent.setAction(Intent.ACTION_SEND);

		shareIntent.putExtra(Intent.EXTRA_TEXT, text);
		shareIntent.setType("text/plain");
		try {
			activity.startActivity(Intent.createChooser(shareIntent, activity.getText(R.string.share_with)));
		} catch (ActivityNotFoundException e) {
			//This should happen only on faulty androids because normally chooser is always available
			Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
		}
	}

	public static void share(Application application, String text) {
		Intent shareIntent = new Intent();
		shareIntent.setAction(Intent.ACTION_SEND);

		shareIntent.putExtra(Intent.EXTRA_TEXT, text);
		shareIntent.setType("text/plain");

		try {
			application.startActivity(Intent.createChooser(shareIntent, application.getText(R.string.share_with)));
		} catch (ActivityNotFoundException e) {
			//This should happen only on faulty androids because normally chooser is always available
			Toast.makeText(application, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
		}
	}

	public static void share(XmppActivity activity, List<Message> messages) {
		Intent shareIntent = new Intent();
		shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);

		ArrayList<Uri> files = new ArrayList<>();
		File firstFile = null;
		String firstFileMimeType = "";
		for (Message m : messages) {
			if (m.isFileOrImage()) {
				final DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(m);
				if (firstFile == null) {
					firstFile = file;
					firstFileMimeType = m.getMimeType();
				}
				files.add(FileBackend.getUriForFile(activity, file));
			}
		}

		try {
			shareIntent.putExtra(Intent.EXTRA_STREAM, files);
		} catch (SecurityException e) {
			String filePath = "";
			if (firstFile != null) {
				filePath = firstFile.getAbsolutePath();
			}

			Toast.makeText(activity, activity.getString(R.string.no_permission_to_access_x, filePath), Toast.LENGTH_SHORT).show();
			return;
		}
		shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		String mime = firstFileMimeType;
		if (mime == null || files.size() > 1) {
			mime = "*/*";
		}
		shareIntent.setType(mime);


		StringBuilder sb = new StringBuilder();
		for (Message m : messages) {
			if (sb.length() != 0) {
				sb.append("\n\n");
			}

			sb.append(m.getAvatarName());
			sb.append(", ");
			sb.append(DateUtils.formatDateTime(activity, m.getTimeSent(), DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH));
			sb.append(": ");
			sb.append(m.getBodyForDisplaying());

			if (m.isGeoUri()) {
				sb.append(m.getBody());
			} else if (!m.isFileOrImage()) {
				sb.append(m.getBodyForDisplaying());
			}
		}

		shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());

		if (files.isEmpty()) {
			shareIntent.setType("text/plain");
		}

		try {
			activity.startActivity(Intent.createChooser(shareIntent, activity.getText(R.string.share_with)));
		} catch (ActivityNotFoundException e) {
			//This should happen only on faulty androids because normally chooser is always available
			Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
		}
	}

	public static void copyToClipboard(XmppActivity activity, Message message) {
		if (activity.copyTextToClipboard(message.getBodyForDisplaying().toString(), R.string.message)) {
			Toast.makeText(activity, R.string.message_copied_to_clipboard, Toast.LENGTH_SHORT).show();
		}
	}

	public static void copyToClipboard(XmppActivity activity, StringBuilder sb) {
		if (activity.copyTextToClipboard(sb.toString(), R.string.message)) {
			Toast.makeText(activity, R.string.message_copied_to_clipboard, Toast.LENGTH_SHORT).show();
		}
	}

	public static void copyUrlToClipboard(XmppActivity activity, Message message) {
		final String url;
		final int resId;
		if (message.isGeoUri()) {
			resId = R.string.location;
			url = message.getBody();
		} else if (message.hasFileOnRemoteHost()) {
			resId = R.string.file_url;
			url = message.getFileParams().url;
		} else {
			final Message.FileParams fileParams = message.getFileParams();
			url = (fileParams != null && fileParams.url != null) ? fileParams.url : message.getBody().trim();
			resId = R.string.file_url;
		}
		if (activity.copyTextToClipboard(url, resId)) {
			Toast.makeText(activity, R.string.url_copied_to_clipboard, Toast.LENGTH_SHORT).show();
		}
	}

	public static void copyLinkToClipboard(final Context context, final String url) {
		final Uri uri = Uri.parse(url);
		if ("xmpp".equals(uri.getScheme())) {
			try {
				final Jid jid = new XmppUri(uri).getJid();
				if (copyTextToClipboard(context, jid.asBareJid().toString(), R.string.account_settings_jabber_id)) {
					Toast.makeText(context, R.string.jabber_id_copied_to_clipboard, Toast.LENGTH_SHORT).show();
				}
			} catch (final Exception e) { }
		} else {
			if (copyTextToClipboard(context, url, R.string.web_address)) {
				Toast.makeText(context, R.string.url_copied_to_clipboard, Toast.LENGTH_SHORT).show();
			}
		}
	}

	public static boolean copyTextToClipboard(Context context, String text, int labelResId) {
		ClipboardManager mClipBoardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		String label = context.getResources().getString(labelResId);
		if (mClipBoardManager != null) {
			ClipData mClipData = ClipData.newPlainText(label, text);
			mClipBoardManager.setPrimaryClip(mClipData);
			return true;
		}
		return false;
	}

    public static void copyLinkToClipboard(final XmppActivity activity, final Message message) {
        final SpannableStringBuilder body = message.getBodyForDisplaying();
        for (final String url : MyLinkify.extractLinks(body)) {
            final Uri uri = Uri.parse(url);
            if ("xmpp".equals(uri.getScheme())) {
                try {
                    final Jid jid = new XmppUri(uri).getJid();
                    if (activity.copyTextToClipboard(
                            jid.asBareJid().toString(), R.string.account_settings_jabber_id)) {
                        Toast.makeText(
                                        activity,
                                        R.string.jabber_id_copied_to_clipboard,
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                    return;
                } catch (final Exception e) {
                    return;
                }
            } else {
                if (activity.copyTextToClipboard(url, R.string.web_address)) {
                    Toast.makeText(activity, R.string.url_copied_to_clipboard, Toast.LENGTH_SHORT)
                            .show();
                }
                return;
            }
        }
    }

    public static String getLinkScheme(final SpannableStringBuilder body) {
        MyLinkify.addLinks(body, false);
        for (final String url : MyLinkify.extractLinks(body)) {
            final Uri uri = Uri.parse(url);
            if ("xmpp".equals(uri.getScheme())) {
                return uri.getScheme();
            } else {
                return "http";
            }
        }
        return null;
    }

	public static void copyJidToClipboard(final XmppActivity activity, Jid jid) {
		if (copyTextToClipboard(activity, jid.toEscapedString(), R.string.account_settings_jabber_id)) {
			Toast.makeText(activity, R.string.jabber_id_copied_to_clipboard, Toast.LENGTH_SHORT).show();
		}
	}
}
