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

package eu.siacs.conversations.ui;

import static androidx.recyclerview.widget.ItemTouchHelper.LEFT;
import static androidx.recyclerview.widget.ItemTouchHelper.RIGHT;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.Optional;
import com.google.common.collect.Collections2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.FragmentConversationsOverviewBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.ui.util.PendingActionHelper;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.ScrollState;
import eu.siacs.conversations.ui.util.SoftKeyboardUtils;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.EasyOnboardingInvite;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.xmpp.jingle.OngoingRtpSession;

public class ConversationsOverviewFragment extends XmppFragment {

	private static final String STATE_SCROLL_POSITION = ConversationsOverviewFragment.class.getName()+".scroll_state";

	private final List<Conversation> conversations = new ArrayList<>();
	private final List<ListItem.Tag> tags = new ArrayList<>();

	private final PendingItem<Conversation> swipedConversation = new PendingItem<>();
	private final PendingItem<ScrollState> pendingScrollState = new PendingItem<>();
	private FragmentConversationsOverviewBinding binding;
	private ConversationAdapter conversationsAdapter;
	private XmppActivity activity;
	private float mSwipeEscapeVelocity = 0f;
	private final PendingActionHelper pendingActionHelper = new PendingActionHelper();

	private final ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0,LEFT|RIGHT) {
		@Override
		public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
			//todo maybe we can manually changing the position of the conversation
			return false;
		}

		@Override
		public float getSwipeEscapeVelocity (float defaultValue) {
			return mSwipeEscapeVelocity;
		}

		@Override
		public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
									float dX, float dY, int actionState, boolean isCurrentlyActive) {
			super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
			if(actionState != ItemTouchHelper.ACTION_STATE_IDLE){
				Paint paint = new Paint();
				paint.setColor(StyledAttributes.getColor(activity, androidx.appcompat.R.attr.colorPrimaryDark));
				paint.setStyle(Paint.Style.FILL);
				c.drawRect(viewHolder.itemView.getLeft(),viewHolder.itemView.getTop()
						,viewHolder.itemView.getRight(),viewHolder.itemView.getBottom(), paint);
			}
		}

		@Override
		public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
			super.clearView(recyclerView, viewHolder);
			viewHolder.itemView.setAlpha(1f);
		}

		@Override
		public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
			pendingActionHelper.execute();
			int position = viewHolder.getLayoutPosition();

			try {
				swipedConversation.push(conversationsAdapter.getConversation(position));
			} catch (IndexOutOfBoundsException e) {
				return;
			}
			conversationsAdapter.remove(swipedConversation.peek(), position);
			activity.xmppConnectionService.markRead(swipedConversation.peek());

			if (position == 0 && conversationsAdapter.getItemCount() == 0) {
				final Conversation c = swipedConversation.pop();
				activity.xmppConnectionService.archiveConversation(c);
				return;
			}
			final boolean formerlySelected = ConversationFragment.getConversation(getActivity()) == swipedConversation.peek();
			if (activity instanceof OnConversationArchived) {
				((OnConversationArchived) activity).onConversationArchived(swipedConversation.peek());
			}
			final Conversation c = swipedConversation.peek();
			final int title;
			if (c.getMode() == Conversational.MODE_MULTI) {
				if (c.getMucOptions().isPrivateAndNonAnonymous()) {
					title = R.string.title_undo_swipe_out_group_chat;
				} else {
					title = R.string.title_undo_swipe_out_channel;
				}
			} else {
				title = R.string.title_undo_swipe_out_conversation;
			}

			final Snackbar snackbar = Snackbar.make(binding.list, title, 5000)
					.setAction(R.string.undo, v -> {
						pendingActionHelper.undo();
						Conversation conversation = swipedConversation.pop();
						conversationsAdapter.insert(conversation, position);
						if (formerlySelected) {
							if (activity instanceof OnConversationSelected) {
								((OnConversationSelected) activity).onConversationSelected(c);
							}
						}
						LinearLayoutManager layoutManager = (LinearLayoutManager) binding.list.getLayoutManager();
						if (position > layoutManager.findLastVisibleItemPosition()) {
							binding.list.smoothScrollToPosition(position);
						}
					})
					.addCallback(new Snackbar.Callback() {
						@Override
						public void onDismissed(Snackbar transientBottomBar, int event) {
							switch (event) {
								case DISMISS_EVENT_SWIPE:
								case DISMISS_EVENT_TIMEOUT:
									pendingActionHelper.execute();
									break;
							}
						}
					});

			pendingActionHelper.push(() -> {
				if (snackbar.isShownOrQueued()) {
					snackbar.dismiss();
				}
				final Conversation conversation = swipedConversation.pop();
				if(conversation != null){
					if (!conversation.isRead() && conversation.getMode() == Conversation.MODE_SINGLE) {
						return;
					}
					activity.xmppConnectionService.archiveConversation(c);
				}
			});

			ThemeHelper.fix(snackbar);
			snackbar.show();
		}
	};

	private ItemTouchHelper touchHelper;

	public static Conversation getSuggestion(Activity activity) {
		final Conversation exception;
		Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.main_fragment);
		if (fragment instanceof ConversationsOverviewFragment) {
			exception = ((ConversationsOverviewFragment) fragment).swipedConversation.peek();
		} else {
			exception = null;
		}
		return getSuggestion(activity, exception);
	}

	public static Conversation getSuggestion(Activity activity, Conversation exception) {
		Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.main_fragment);
		if (fragment instanceof ConversationsOverviewFragment) {
			List<Conversation> conversations = ((ConversationsOverviewFragment) fragment).conversations;
			if (conversations.size() > 0) {
				Conversation suggestion = conversations.get(0);
				if (suggestion == exception) {
					if (conversations.size() > 1) {
						return conversations.get(1);
					}
				} else {
					return suggestion;
				}
			}
		}
		return null;

	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		if (savedInstanceState == null) {
			return;
		}
		pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if (activity instanceof XmppActivity) {
			this.activity = (XmppActivity) activity;
		} else {
			throw new IllegalStateException("Trying to attach fragment to activity that is not an XmppActivity");
		}
	}
	@Override
	public void onDestroyView() {
		Log.d(Config.LOGTAG,"ConversationsOverviewFragment.onDestroyView()");
		super.onDestroyView();
		this.binding = null;
		this.conversationsAdapter = null;
		this.touchHelper = null;
	}
	@Override
	public void onDestroy() {
		Log.d(Config.LOGTAG,"ConversationsOverviewFragment.onDestroy()");
		super.onDestroy();

	}
	@Override
	public void onPause() {
		Log.d(Config.LOGTAG,"ConversationsOverviewFragment.onPause()");
		pendingActionHelper.execute();
		super.onPause();
	}

	@Override
	public void onDetach() {
		super.onDetach();
		this.activity = null;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.mSwipeEscapeVelocity = getResources().getDimension(R.dimen.swipe_escape_velocity);
		this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment_conversations_overview, container, false);
		this.binding.fab.setOnClickListener((view) -> StartConversationActivity.launch(getActivity()));

		this.conversationsAdapter = new ConversationAdapter(this.activity, this.conversations, this.tags);
		this.conversationsAdapter.setConversationClickListener((view, conversation) -> {
			if (activity instanceof OnConversationSelected) {
				((OnConversationSelected) activity).onConversationSelected(conversation);
			} else {
				Log.w(ConversationsOverviewFragment.class.getCanonicalName(), "Activity does not implement OnConversationSelected");
			}
		});
		this.binding.list.setAdapter(this.conversationsAdapter);
		this.binding.list.setLayoutManager(new LinearLayoutManager(getActivity(),LinearLayoutManager.VERTICAL,false));
		registerForContextMenu(this.binding.list);
		this.touchHelper = new ItemTouchHelper(this.callback);
		this.touchHelper.attachToRecyclerView(this.binding.list);
		return binding.getRoot();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
		menuInflater.inflate(R.menu.fragment_conversations_overview, menu);
		AccountUtils.showHideMenuItems(menu);
		final MenuItem easyOnboardInvite = menu.findItem(R.id.action_easy_invite);
		MenuItem noteToSelf = menu.findItem(R.id.action_note_to_self);
		easyOnboardInvite.setVisible(EasyOnboardingInvite.anyHasSupport(activity == null ? null : activity.xmppConnectionService));

		if (activity == null || activity.xmppConnectionService == null || activity.xmppConnectionService.getAccounts().size() != 1) {
			noteToSelf.setVisible(false);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
		activity.getMenuInflater().inflate(R.menu.conversations, menu);

		final MenuItem menuMucDetails = menu.findItem(R.id.action_muc_details);
		final MenuItem menuContactDetails = menu.findItem(R.id.action_contact_details);
		final MenuItem menuMute = menu.findItem(R.id.action_mute);
		final MenuItem menuUnmute = menu.findItem(R.id.action_unmute);
		final MenuItem menuOngoingCall = menu.findItem(R.id.action_ongoing_call);
		final MenuItem menuTogglePinned = menu.findItem(R.id.action_toggle_pinned);

		if (menuInfo == null) return;
		int pos = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
		if (pos < 0) return;
		Conversation conversation = conversationsAdapter.getConversation(pos);
		if (conversation != null) {
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				menuContactDetails.setVisible(false);
				menuMucDetails.setTitle(conversation.getMucOptions().isPrivateAndNonAnonymous() ? R.string.action_muc_details : R.string.channel_details);
				menuOngoingCall.setVisible(false);
			} else {
				final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
				final Optional<OngoingRtpSession> ongoingRtpSession = service == null ? Optional.absent() : service.getJingleConnectionManager().getOngoingRtpConnection(conversation.getContact());
				if (ongoingRtpSession.isPresent()) {
					menuOngoingCall.setVisible(true);
				} else {
					menuOngoingCall.setVisible(false);
				}
				menuContactDetails.setVisible(!conversation.withSelf());
				menuMucDetails.setVisible(false);
			}
			if (conversation.isMuted()) {
				menuMute.setVisible(false);
			} else {
				menuUnmute.setVisible(false);
			}
			if (conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false)) {
				menuTogglePinned.setTitle(R.string.remove_from_favorites);
			} else {
				menuTogglePinned.setTitle(R.string.add_to_favorites);
			}
		}
		super.onCreateContextMenu(menu, view, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		Conversation conversation = conversationsAdapter.getConversation(((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position);
		if (conversation == null) {
			return false;
		}

		ConversationFragment fragment = new ConversationFragment();
		fragment.setHasOptionsMenu(false);
		fragment.onAttach(activity);
		fragment.reInit(conversation, null);
		boolean r = fragment.onOptionsItemSelected(item);
		refresh();
		return r;
	}

	@Override
	public void onBackendConnected() {
		refresh();
	}

	@Override
	public void onSaveInstanceState(Bundle bundle) {
		super.onSaveInstanceState(bundle);
		ScrollState scrollState = getScrollState();
		if (scrollState != null) {
			bundle.putParcelable(STATE_SCROLL_POSITION, scrollState);
		}
	}

	private ScrollState getScrollState() {
		if (this.binding == null) {
			return null;
		}
		LinearLayoutManager layoutManager = (LinearLayoutManager) this.binding.list.getLayoutManager();
		int position = layoutManager.findFirstVisibleItemPosition();
		final View view = this.binding.list.getChildAt(0);
		if (view != null) {
			return new ScrollState(position,view.getTop());
		} else {
			return new ScrollState(position, 0);
		}
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		boolean navBarVisible = activity instanceof ConversationsActivity && ((ConversationsActivity) activity).navigationBarVisible();
		MenuItem manageAccount = menu.findItem(R.id.action_account);
		MenuItem manageAccounts = menu.findItem(R.id.action_accounts);
		if (navBarVisible) {
			manageAccount.setVisible(false);
			manageAccounts.setVisible(false);
		} else {
			AccountUtils.showHideMenuItems(menu);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onStart()");
		if (activity.xmppConnectionService != null) {
			refresh();
		}

		if (activity instanceof ConversationsActivity) {
			boolean showed = ((ConversationsActivity) activity).showNavigationBar();

			if (showed) {
				this.binding.fab.setVisibility(View.GONE);
			} else {
				this.binding.fab.setVisibility(View.VISIBLE);
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onResume()");
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (MenuDoubleTabUtil.shouldIgnoreTap()) {
			return false;
		}
		switch (item.getItemId()) {
			case R.id.action_search:
				startActivity(new Intent(getActivity(), SearchActivity.class));
				return true;
			case R.id.action_easy_invite:
				selectAccountToStartEasyInvite();
				return true;
			case R.id.action_note_to_self:
				final List<Account> accounts = activity.xmppConnectionService.getAccounts();
				if (accounts.size() == 1) {
					final Contact self = new Contact(accounts.get(0).getSelfContact());
					Conversation conversation = activity.xmppConnectionService.findOrCreateConversation(self.getAccount(), self.getJid(), null, false, false, true, null);
					SoftKeyboardUtils.hideSoftKeyboard(activity);
					activity.switchToConversation(conversation);
				}
		}
		return super.onOptionsItemSelected(item);
	}

	private void selectAccountToStartEasyInvite() {
		final List<Account> accounts = EasyOnboardingInvite.getSupportingAccounts(activity.xmppConnectionService);
		if (accounts.size() == 0) {
			//This can technically happen if opening the menu item races with accounts reconnecting or something
			Toast.makeText(getActivity(),R.string.no_active_accounts_support_this, Toast.LENGTH_LONG).show();
		} else if (accounts.size() == 1) {
			openEasyInviteScreen(accounts.get(0));
		} else {
			final AtomicReference<Account> selectedAccount = new AtomicReference<>(accounts.get(0));
			final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
			alertDialogBuilder.setTitle(R.string.choose_account);
			final String[] asStrings = Collections2.transform(accounts, a -> a.getJid().asBareJid().toEscapedString()).toArray(new String[0]);
			alertDialogBuilder.setSingleChoiceItems(asStrings, 0, (dialog, which) -> selectedAccount.set(accounts.get(which)));
			alertDialogBuilder.setNegativeButton(R.string.cancel, null);
			alertDialogBuilder.setPositiveButton(R.string.ok, (dialog, which) -> openEasyInviteScreen(selectedAccount.get()));
			alertDialogBuilder.create().show();
		}
	}

	private void openEasyInviteScreen(final Account account) {
		EasyOnboardingInviteActivity.launch(account, activity);
	}

	@Override
	void refresh() {
		if (this.binding == null || this.activity == null) {
			Log.d(Config.LOGTAG,"ConversationsOverviewFragment.refresh() skipped updated because view binding or activity was null");
			return;
		}
		this.activity.xmppConnectionService.populateWithOrderedConversations(this.conversations);
		Conversation removed = this.swipedConversation.peek();
		if (removed != null) {
			if (removed.isRead()) {
				this.conversations.remove(removed);
			} else {
				pendingActionHelper.execute();
			}
		}

		refreshTags();
		this.conversationsAdapter.notifyDataSetChanged();
		ScrollState scrollState = pendingScrollState.pop();
		if (scrollState != null) {
			setScrollPosition(scrollState);
		}
	}

	private void refreshTags() {
		this.conversationsAdapter.setGroupingEnabled(activity.xmppConnectionService.getPreferences().getBoolean("conversationsGroupByTags", false));

		if (conversationsAdapter.isGroupingEnabled()) {
			List<ListItem.Tag> tags = new ArrayList<>();
			final List<Account> accounts = activity.xmppConnectionService.getAccounts();
			for (final Account account : accounts) {
				if (account.isEnabled()) {
					for (Contact contact : account.getRoster().getContacts()) {
						if (contact.showInContactList()) {
							tags.addAll(contact.getTags(activity));
						}
					}

					for (Bookmark bookmark : account.getBookmarks()) {
						tags.addAll(bookmark.getTags(activity));
					}
				}
			}

			Comparator<Map.Entry<ListItem.Tag, Integer>> sortTagsBy = Map.Entry.comparingByValue(Comparator.reverseOrder());
			sortTagsBy = sortTagsBy.thenComparing(entry -> entry.getKey().getName());

			this.tags.clear();
			this.tags.addAll(
					tags.stream()
							.collect(Collectors.toMap((x) -> x, (t) -> 1, (c1, c2) -> c1 + c2))
							.entrySet().stream()
							.sorted(sortTagsBy)
							.map(e -> e.getKey()).collect(Collectors.toList())
			);

			ListItem.Tag channelTag = null;
			int channelTagIndex = 0;

			for (ListItem.Tag tag : this.tags) {
				if (tag.getName().equals("Channel")) {
					channelTag = tag;
					break;
				}
				channelTagIndex++;
			}

			if (channelTag != null) {
				this.tags.remove(channelTagIndex);
				this.tags.add(0, channelTag);
			}
		}
	}

	private void setScrollPosition(ScrollState scrollPosition) {
		if (scrollPosition != null) {
			LinearLayoutManager layoutManager = (LinearLayoutManager) binding.list.getLayoutManager();
			layoutManager.scrollToPositionWithOffset(scrollPosition.position, scrollPosition.offset);
		}
	}
}
