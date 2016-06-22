/*
 *   Copyright (C) 2015 Alex Neeky
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.slx.funstream.ui.chat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.slx.funstream.App;
import com.slx.funstream.BuildConfig;
import com.slx.funstream.R;
import com.slx.funstream.adapters.ChatAdapter;
import com.slx.funstream.adapters.SimpleUserArrayAdapter;
import com.slx.funstream.auth.UserStore;
import com.slx.funstream.chat.ChatApiUtils;
import com.slx.funstream.chat.ChatService;
import com.slx.funstream.chat.SmileRepo;
import com.slx.funstream.chat.events.ChatErrorEvent;
import com.slx.funstream.chat.events.ChatUserListEvent;
import com.slx.funstream.chat.events.SmileLoadEvent;
import com.slx.funstream.model.ChatMessage;
import com.slx.funstream.model.ChatUser;
import com.slx.funstream.model.Message;
import com.slx.funstream.model.SystemMessage;
import com.slx.funstream.rest.model.ChatListRequest;
import com.slx.funstream.rest.model.CurrentUser;
import com.slx.funstream.rest.model.Smile;
import com.slx.funstream.rest.services.FunstreamApi;
import com.slx.funstream.ui.chat.SmileGridView.OnSmileClickListener;
import com.slx.funstream.ui.chat.SmileKeyboard.OnSmileBackspaceClickListener;
import com.slx.funstream.ui.chat.SmileKeyboard.OnSoftKeyboardOpenCloseListener;
import com.slx.funstream.utils.LogUtils;
import com.slx.funstream.utils.PrefUtils;
import com.slx.funstream.utils.RxBus;
import com.squareup.leakcanary.RefWatcher;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.trello.rxlifecycle.components.support.RxFragment;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static android.text.TextUtils.isEmpty;


public class ChatFragment extends RxFragment
		implements
		OnSmileClickListener,
		OnSmileBackspaceClickListener,
		OnSoftKeyboardOpenCloseListener,
		PopupWindow.OnDismissListener,
		ChatAdapter.OnChatMessageClick,
        ServiceConnection {
    private static final String TAG = "ChatFragment";
    public static final String CHANNEL_NAME = "channel_name";
	public static final String CHANNEL_ID = "channel_id";

	@Bind(R.id.etNewMessage)
	EditText etNewMessage;
	@Bind(R.id.sendMessage)
	ImageButton sendMessage;
	@Bind(R.id.ibSmiles)
	ImageButton ibSmiles;
	@Bind(R.id.contentRoot)
	RelativeLayout contentRoot;
	@Bind(R.id.rvChat)
	RecyclerView rvChat;
	@Bind(R.id.chat_controls)
	LinearLayout chatControls;
	@Bind(R.id.progressBar)
	ProgressBar progressBar;
	@Bind(R.id.tvServerMessages)
	TextView tvServerMessages;
	@Bind(R.id.progress_layout)
	LinearLayout progressLayout;
	@Bind(R.id.tvTo)
	TextView tvTo;
	@Bind(R.id.chat_buttons_layout)
	LinearLayout chatButtonsLayout;
	@Bind(R.id.tvUserList)
	TextView tvUserList;
	@Bind(R.id.chat_layout)
	FrameLayout chatLayout;
	@Bind(R.id.btNewMessages)
	Button btNewMessages;
	@Bind(R.id.btLoadOldMessages)
	Button btLoadOldMessages;

	@Inject
	PrefUtils prefUtils;
	@Inject
	SmileRepo smileRepo;
	@Inject
	Picasso picasso;
	@Inject
	Context context;
	@Inject
	UserStore userStore;
	@Inject
    FunstreamApi funstreamApi;
    @Inject
    RxBus rxBus;

	private SmileKeyboard smileKeyboard;
//	private SmileHandler smileHandler;
	private List<EditTextTarget> targets = new ArrayList<>();

	private boolean isScrollToBottom = true;
	private LinearLayoutManager mLinearLayoutManager;

	private long channel_id = -1;
	private String channel_name = "";

	private ChatAdapter chatAdapter;
	private ChatMessage newMessage = null;

	private ChatService mService;
	private Integer[] userIds;

    // Smiley image spansText field
    private List<ImageSpan> imageSpans = new LinkedList<>();
    private List<Message> chatMessages = new LinkedList<>();
    private CurrentUser currentUser;
	private int smileSizeMultiplier;


	public ChatFragment() {
	}

	public static ChatFragment newInstance(long channelId, String channelName) {
		ChatFragment chatFragment = new ChatFragment();
		Bundle args = new Bundle();
		args.putLong(CHANNEL_ID, channelId);
		args.putString(CHANNEL_NAME, channelName);
		chatFragment.setArguments(args);

		return chatFragment;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		App.applicationComponent().inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		if (args != null) {
			channel_id = args.getLong(CHANNEL_ID);
			channel_name = args.getString(CHANNEL_NAME);
		}
        
		chatAdapter = new ChatAdapter(getActivity());
		chatAdapter.updateChatAdapter(chatMessages);

        // Start Chat message receiver service
		Intent startIntent = new Intent(context, ChatService.class);
		startIntent.putExtra(CHANNEL_ID, channel_id);
		context.startService(startIntent);

		smileRepo.loadSmiles();
		smileSizeMultiplier = context.getResources().getInteger(R.integer.smile_size_multiplier);
		setHasOptionsMenu(true);
	}


	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_chat, container, false);
		ButterKnife.bind(this, view);
		return view;
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

        AppCompatActivity activity = (AppCompatActivity) getActivity();
		if (activity.getSupportActionBar() != null) {
			activity.getSupportActionBar().setTitle(channel_name);
		}

		mLinearLayoutManager = new LinearLayoutManager(context);
		mLinearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
		rvChat.setLayoutManager(mLinearLayoutManager);
		rvChat.setAdapter(chatAdapter);
//		RecyclerView.ItemDecoration itemDecoration = new
//				DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL_LIST);
//		rvChat.addItemDecoration(itemDecoration);
		rvChat.setItemAnimator(new DefaultItemAnimator());
		rvChat.setHasFixedSize(false);

		etNewMessage.setOnEditorActionListener((v, actionId, event) -> {
			boolean handled = false;
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				sendMessage();
				handled = true;
			}
			return handled;
		});

		RxTextView
				.beforeTextChangeEvents(etNewMessage)
				.subscribe(e -> {
                    int count = e.count();
                    int start = e.start();

                    // Check if some text will be removed.
                    if (count > 0) {
                        int end = start + count;
                        Editable message = etNewMessage.getEditableText();
                        ImageSpan[] list = message.getSpans(start, end, ImageSpan.class);

                        for (ImageSpan span : list) {
                            // Get only the smile that are inside of the changed
                            // region.
                            int spanStart = message.getSpanStart(span);
                            int spanEnd = message.getSpanEnd(span);
                            if ((spanStart < end) && (spanEnd > start)) {
                                // Add to remove list
                                imageSpans.add(span);
                            }
                        }
                    }
                });

		RxTextView
				.afterTextChangeEvents(etNewMessage)
				.subscribe(e -> {
                    Editable message = e.editable();

                    // Commit the smile to be removed.
                    for (ImageSpan span : imageSpans) {
                        int start = message.getSpanStart(span);
                        int end = message.getSpanEnd(span);

                        // Remove the span
                        message.removeSpan(span);

                        // Remove the remaining smile text.
                        if (start != end) {
                            message.delete(start, end);
                        }
                    }
                    imageSpans.clear();
				});

        // Handle application bus
        rxBus
                .toObserverable()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Object>() {
                    @Override
                    public void onCompleted() {
                        Log.d(TAG, "onCompleted rxBus");
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Object event) {
                        if (event instanceof SmileLoadEvent) {
                            Log.d(TAG, "onCompleted: SmileLoadEvent");
                            if (smileKeyboard == null) {
                                createSmileKeyboardPopUp();
                                showChatViews();
                            }
                        } else if (event instanceof ChatErrorEvent) {
                            Snackbar.make(contentRoot,
                                    ((ChatErrorEvent) event).getMessage(), Snackbar.LENGTH_LONG)
                                   .show();
                        } else if (event instanceof ChatUserListEvent) {
                            userIds = ((ChatUserListEvent) event).getUsers();
                            updateUserList(userIds);
                        }
                    }
                });

        // Handle Chat controls
		userStore
                .fetchUser()
                .subscribeOn(Schedulers.io())
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<CurrentUser>() {
                    @Override
                    public void onCompleted() {
                        Log.d(TAG, "fetchUser onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "fetchUser onError " + e);
						e.printStackTrace();
                    }

                    @Override
                    public void onNext(CurrentUser user) {
                        Log.d(TAG, "userStore->fetchUser->onNext " + user);
                        currentUser = user;
                        if (currentUser != null && !isEmpty(user.getToken())) {
                            showChatControls(true);
                        }

//                		// Check if user has logged in and token is exists
//                		if (userStore.isUserLoggedIn()) {
//                			// Show chat controls
//                			showChatControls(false);
//                		} else {
//                			// Hide chat controls
//                			showChatControls(true);
//                		}
                    }
                });

//		// Create the smile handler
//		smileHandler = new SmileHandler(etNewMessage);
	}

    private void updateUserList(Integer[] userIds) {
        tvUserList.setText(String.valueOf(userIds.length));
    }

    private void showChatViews() {
		progressLayout.setVisibility(View.GONE);
		chatLayout.setVisibility(View.VISIBLE);
		chatControls.setVisibility(View.VISIBLE);
	}

	@Override
	public void onStart() {
		super.onStart();
		bindService();

//        chatAdapter.notifyDataSetChanged();
//        scrollToBottom();
//


//        Log.i(LogUtils.TAG, "User list ids loaded size = " + ids.length);
//        userIds = ids;
//        tvUserList.setText(String.valueOf(userIds.length));
	}

	@Override
	public void onStop() {
		super.onStop();
		unBindService();
	}

	@Override
	public void onResume() {
		super.onResume();
		isScrollToBottom = prefUtils.isScroll();

		// Subscribe to keyboard listeners
		if (smileKeyboard != null) {
			smileKeyboard.setOnSoftKeyboardOpenCloseListener(this);
			smileKeyboard.setOnSmileClickedListener(this);
			smileKeyboard.setOnSmileBackspaceClickedListener(this);
			smileKeyboard.setOnDismissListener(this);
		}

		// Subscribe to chat adapter events
		chatAdapter.setOnChatMessageClickListener(this);
		rvChat.addOnScrollListener(onScrollListener);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (smileKeyboard != null) {
			smileKeyboard.setOnSoftKeyboardOpenCloseListener(null);
			smileKeyboard.setOnSmileClickedListener(null);
			smileKeyboard.setOnSmileBackspaceClickedListener(null);
			smileKeyboard.setOnDismissListener(null);
		}
		chatAdapter.setOnChatMessageClickListener(null);
		rvChat.removeOnScrollListener(onScrollListener);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.menu_chat, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		if (channel_id == ChatApiUtils.CHANNEL_MAIN ||
				channel_id == ChatApiUtils.CHANNEL_ADMIN) { // ||!userStore.isUserLoggedIn()
			MenuItem menuItem = menu.findItem(R.id.action_message_to_streamer);
			menuItem.setVisible(false);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();

		if (id == R.id.action_message_to_streamer) {
			if (currentUser != null) {
				newMessage = new ChatMessage();
				newMessage.setTo(new ChatUser(channel_id, channel_name));
				if (!etNewMessage.isFocused()) etNewMessage.requestFocus();
				tvTo.setText(channel_name);
				tvTo.setVisibility(View.VISIBLE);
			}
			return true;
		}

		return super.onOptionsItemSelected(item);
	}


	@Override
	public void onDestroyView() {
		super.onDestroyView();
		ButterKnife.unbind(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		targets = null;
		context.stopService(new Intent(context, ChatService.class));
		if (BuildConfig.DEBUG) {
			RefWatcher refWatcher = App.getRefWatcher(getActivity());
			refWatcher.watch(this);
		}

	}

	private void showChatControls(boolean show) {
		if (show) {
            // Show chat buttons
            chatButtonsLayout.setVisibility(View.VISIBLE);
//			btLogin.setVisibility(View.GONE);
		} else {
            // Hide chat buttons
            chatButtonsLayout.setVisibility(View.GONE);
//			btLogin.setVisibility(View.VISIBLE);
		}
	}

	private void scrollToBottom() {
		if (isScrollToBottom) {
			int firstItemPos = chatAdapter.getItemCount();
			if (firstItemPos == 0) return;
			mLinearLayoutManager.scrollToPosition(firstItemPos - 1);
		}
	}

//	private static void changeSmileKeyboardIcon(ImageButton iconToBeChanged, int drawableResourceId) {
//		iconToBeChanged.setImageResource(drawableResourceId);
//	}

//	@OnClick(R.id.btLogin)
//	protected void login() {
//		startActivityForResult(new Intent(context, LoginActivity.class), RC_LOGIN);
//	}

	@OnClick(R.id.sendMessage)
	protected void sendMessage() {
        if (currentUser != null) {
            if (newMessage == null) {
                newMessage = new ChatMessage();
                newMessage.setTo(null);
            }

            newMessage.setChannel(channel_id);
            newMessage.setFrom(new ChatUser(currentUser.getId(), currentUser.getName()));
            newMessage.setText(etNewMessage.getText().toString());

            if (mService != null) {
                mService.sendMessage(newMessage);
                clearMessageBox();
            }
            clearTo();
        }
    }

	private void clearMessageBox() {
		etNewMessage.getText().clear();
	}

	private void createSmileKeyboardPopUp() {
        smileKeyboard = new SmileKeyboard(getContext(), contentRoot, smileRepo.getSmiles().values());
        smileKeyboard.setAnimationStyle(R.style.keyboard_animation);
        smileKeyboard.setSizeForSoftKeyboard();
        smileKeyboard.setOnSoftKeyboardOpenCloseListener(ChatFragment.this);
        smileKeyboard.setOnSmileClickedListener(ChatFragment.this);
        smileKeyboard.setOnSmileBackspaceClickedListener(ChatFragment.this);
        smileKeyboard.setOnDismissListener(ChatFragment.this);
	}

	@Override
	public void onChatMessageClicked(ChatMessage chatMessage) {
		makeTo(chatMessage.getFrom());
	}

	@OnClick(R.id.tvUserList)
	void showUserListDialog() {
        if (userIds != null) {
            funstreamApi.getChatUsers(new ChatListRequest(userIds))
                    .subscribeOn(Schedulers.io())
                    .compose(bindToLifecycle())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Subscriber<ChatUser[]>() {
                        @Override
                        public void onCompleted() {

                        }

                        @Override
                        public void onError(Throwable e) {
                            e.printStackTrace();
                        }

                        @Override
                        public void onNext(ChatUser[] chatUsers) {
                            createUserListDialog(chatUsers);
                        }
                    });
        }
	}

	private void createUserListDialog(ChatUser[] chatUsers){
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.ListDialogStyle);
		builder.setTitle(getString(R.string.dialog_users_title));

		final SimpleUserArrayAdapter arrayAdapter = new SimpleUserArrayAdapter(getContext(), R.layout.row_user, chatUsers);
		builder.setAdapter(arrayAdapter, (dialogInterface, i) -> {
			Log.i(LogUtils.TAG, arrayAdapter.getItem(i).getName());
			makeTo(arrayAdapter.getItem(i));
		});
		builder.show();
	}

	private void makeTo(ChatUser user){
		if (currentUser == null) return;

        newMessage = new ChatMessage();
		newMessage.setTo(new ChatUser(user.getId(), user.getName()));
		if (!etNewMessage.isFocused()) etNewMessage.requestFocus();
		tvTo.setText(user.getName());
		tvTo.setVisibility(View.VISIBLE);
	}

	@OnClick(R.id.tvTo)
	protected void clearTo() {
		if (newMessage != null) newMessage = null;
		tvTo.setText("");
		tvTo.setVisibility(View.GONE);
	}

    public void addSmile(Smile smile) {
        int start = etNewMessage.getSelectionStart()-1;
        int end = etNewMessage.getSelectionEnd();
        EditTextTarget target = new EditTextTarget(start, end, etNewMessage, smile);
        targets.add(target);
        picasso.load(smile.getUrl())
				.resize(smile.getWidth()*2, smile.getHeight()*2)
                .into(target);

    }

//	private class SmileHandler implements TextWatcher {
//		private final EditText mEditText;
//		private final ArrayList<ImageSpan> imageSpans = new ArrayList<>();
//
//		public SmileHandler(EditText editText) {
//			mEditText = editText;
//			mEditText.addTextChangedListener(this);
//		}
//
//		public void insert(Smile smile) {
//			int start = mEditText.getSelectionStart();
//			int end = mEditText.getSelectionEnd();
//			EditTextTarget target = new EditTextTarget(start, end, mEditText, smile);
//			targets.add(target);
//			picasso.load(smile.getUrl())
//					.into(target);
//
//		}
//
//		@Override
//		public void beforeTextChanged(CharSequence text, int start, int count, int after) {
//			// Check if some text will be removed.
//			if (count > 0) {
//				int end = start + count;
//				Editable message = mEditText.getEditableText();
//				ImageSpan[] list = message.getSpans(start, end, ImageSpan.class);
//
//				for (ImageSpan span : list) {
//					// Get only the smile that are inside of the changed
//					// region.
//					int spanStart = message.getSpanStart(span);
//					int spanEnd = message.getSpanEnd(span);
//					if ((spanStart < end) && (spanEnd > start)) {
//						// Add to remove list
//						imageSpans.add(span);
//					}
//				}
//			}
//		}
//
//		@Override
//		public void afterTextChanged(Editable text) {
//			Editable message = mEditText.getEditableText();
//
//			// Commit the smile to be removed.
//			for (ImageSpan span : imageSpans) {
//				int start = message.getSpanStart(span);
//				int end = message.getSpanEnd(span);
//
//				// Remove the span
//				message.removeSpan(span);
//
//				// Remove the remaining smile text.
//				if (start != end) {
//					message.delete(start, end);
//				}
//			}
//			imageSpans.clear();
//		}
//
//		@Override
//		public void onTextChanged(CharSequence text, int start, int before, int count) {
//		}
//	}

	private class EditTextTarget implements Target {
		int start;
		int end;
		EditText editText;
		Smile smile;

		public EditTextTarget(int start, int end, EditText editText, Smile smile) {
			this.start = start;
			this.end = end;
			this.editText = editText;
			this.smile = smile;
		}

		@Override
		public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
			BitmapDrawable bitmapDrawable = new BitmapDrawable(context.getResources(), bitmap);
			bitmapDrawable.setBounds(
					0,
					0,
					bitmapDrawable.getIntrinsicWidth() + 5,
					bitmapDrawable.getIntrinsicHeight() + 5);
			ImageSpan imageSpan = new ImageSpan(bitmapDrawable, ImageSpan.ALIGN_BOTTOM);

			Editable message = editText.getEditableText();

			// Insert the smile
			message.replace(start + 1, end, smile.getCode());
			message.setSpan(imageSpan, start + 1, start + smile.getCode().length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}

		@Override
		public void onBitmapFailed(Drawable errorDrawable) {
			Log.d(LogUtils.TAG, "onBitmapFailed");
		}

		@Override
		public void onPrepareLoad(Drawable placeHolderDrawable) {
			//
		}
	}

	@Override
	public void onSmileClicked(Smile smile) {
		if (etNewMessage == null || smile == null) return;
		Log.d(LogUtils.TAG, smile.toString());
        // add smile to Text Field
        addSmile(smile);
	}

	@Override
	public void onSmileBackspaceClick(View v) {
		if (etNewMessage == null) return;
		KeyEvent event = new KeyEvent(0, 0, 0, KeyEvent.KEYCODE_DEL, 0, 0, 0, 0, KeyEvent.KEYCODE_ENDCALL);
		etNewMessage.dispatchKeyEvent(event);
	}

	@Override
	public void onKeyboardOpen(int keyBoardHeight) {
	}

	@Override
	public void onKeyboardClose() {
		if (smileKeyboard.isShowing()) smileKeyboard.dismiss();
	}

	@Override
	public void onDismiss() {
		//changeSmileKeyboardIcon(ibSmiles, R.drawable.);
	}

	@OnClick(R.id.ibSmiles)
	public void showSmileKeyboard() {
		if (!smileKeyboard.isShowing()) {
			if (smileKeyboard.isKeyBoardOpen()) {
				smileKeyboard.showAtBottom();
//				changeSmileKeyboardIcon(ibSmiles, R.drawable.ic_action_keyboard);
			} else {
				etNewMessage.setFocusableInTouchMode(true);
				etNewMessage.requestFocus();
				smileKeyboard.showAtBottomPending();
				final InputMethodManager inputMethodManager = (InputMethodManager)
						context.getSystemService(Context.INPUT_METHOD_SERVICE);
				inputMethodManager.showSoftInput(etNewMessage, InputMethodManager.SHOW_IMPLICIT);
//				changeSmileKeyboardIcon(ibSmiles, R.drawable.ic_action_keyboard);
			}
		} else {
			smileKeyboard.dismiss();
			// Dismiss a soft keyboard ?
		}
	}

    @OnClick(R.id.btLoadOldMessages)
    void loadHistory() {
        if (mService != null) {
            mService.loadHistory();
        }
    }

	private void bindService() {
		Log.d(TAG, "bindService");
		Intent bindIntent = new Intent(context, ChatService.class);
		context.bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
	}

	private void unBindService() {
		context.unbindService(this);
	}

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "onServiceConnected");
        mService = ((ChatService.LocalBinder) service).getService();

		subscribeNewMessages();
    }

	private void subscribeNewMessages() {
        if (mService != null) {
            mService.getChatMessagesObservable()
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(bindToLifecycle())
                    .subscribe(new Subscriber<Message>() {
                        @Override
                        public void onCompleted() {
                            Log.d(TAG, "onCompleted: ChatMessagesObservable");
                        }

                        @Override
                        public void onError(Throwable e) {
                            Log.e(TAG, "ChatMessagesObservable onError: e="+e);
                        }

                        @Override
                        public void onNext(Message message) {
//                      Log.d(TAG, "onNext: ChatMessage " + chatMessage);
                            chatMessages.add(message);
                            final int size = chatMessages.size();
                            chatAdapter.notifyItemInserted(size);
                            scrollToBottom();
                        }
                    });
        }
	}

	@Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "onServiceDisconnected");
        if (mService != null) {
            mService = null;
        }
    }

    RecyclerView.OnScrollListener onScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            Log.i(LogUtils.TAG, "newState="+newState + "");
            // 0 finger up
            // 1 scroll active
            if (newState == 1) isScrollToBottom = false;
            else isScrollToBottom = true;
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
        }
    };
}
