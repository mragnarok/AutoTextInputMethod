package cn.queshw.autotextinputmethod;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import cn.queshw.autotextsetting.DBOperations;
import cn.queshw.autotextsetting.MethodItem;

@SuppressLint("SimpleDateFormat")
public class AutotextInputMethod extends InputMethodService {
	private int TOASTDURATION = 50;
	private InputConnection mConnection;
	private EditorInfo mEditInfo;
	private int currentCursorStart = -1;// ��ǰ��꿪ʼλ�ã����ڴ�onUpdateSelection�л����¹��λ��
	private int currentCursorEnd = -1;// ��ǰ������λ�ã����ڴ�onUpdateSelection�л����¹��λ��
	private int offsetBefore;// ��Ե�ǰ���
	private int offsetAfter;// ��Ե�ǰ���

	private int mMetaState = 0;
	private long state = 0; // for metakeylistener
	private String mBeforeSubString;// �滻ǰ���ı�
	private StringBuilder mAfterSubString;// �滻����ı�
	private String mUndoSubString;//ɾ�����ı�������undo����
	private int mEnd;// ���ڱ���ĳ�����λ��
	private int mStart;// ���ڱ���ĳ�����λ��
	private int mMoveWhichEnd = 1;//���ڱ�ǣ�ѡ�����ƶ���Ӧ���ƶ�ͷ��0������β��1��
	//private int mPreEnd;// ������λ�ã�����undo
	//private int mPreStart;//������λ�ã�����undo
	ClipboardManager clipboard; //���ڸ��ƣ�ճ����undo�ȹ���

	private Autotext autotext;// ���ڼ�¼�滻��Ϣ
	private CursorOperator curOper;

	private DBOperations dboper;// �������ݿ����
	private int selectedMethodPostion = 0;// ��spinner��ѡ��Ĭ�����뷨
	private int defaultMethodId;// ��spinner��ѡ��Ĭ�����뷨��id
	private CurrentStatusIcon curStatusIcon;// ��ǰ��״̬ͼ��
	private int maxInputLength;// �������input�ĳ��ȣ������������滻��ʱ�����Ҫ�ӹ��ǰ��ȡ�೤���ı�

	private ArrayList<MethodItem> methodItemList;//

	// ���ڱ�ǹ��ܼ��Ƿ���
	private boolean isCtrlPressed;
	private boolean isAltPressed;
	@SuppressWarnings("unused")
	private boolean isCapPressed;
	private boolean isSymPressed;
	private boolean switchToFullScreen = false;// �Ƿ��л���ȫ��ģʽ

	// /////////////////////////////////////////////////////////////////////////
	@Override
	public void onCreate() {
		// //Log.d("Here", "onCreate()");
		super.onCreate();
		dboper = new DBOperations(this);
	}

	// /////////////////////////////////////////////////////////////////////////
	// ֱ�ӷ���true�������Ļ��������Ƿ����������̣�inputview������ʾ
	@Override
	public boolean onEvaluateInputViewShown() {
		// //Log.d("Here", "onEvaluateInputViewShown()");
		return true;
	}

	// //////////////////////////////////////////////////////////////////////////
	// ������ȫ��ģʽ
	@Override
	public boolean onEvaluateFullscreenMode() {
		// TODO Auto-generated method stub
		return switchToFullScreen;
	}

	// //////////////////////////////////////////////////////////////////////////
	// ����inputview
	@Override
	public View onCreateInputView() {
		// //Log.d("Here", "onCreateInputView()");
		return null;
	}

	// ///////////////////////////////////////////////////////////////////////////
	@Override
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		// //Log.d("Here", "onStartInput()");
		super.onStartInput(attribute, restarting);

		mConnection = this.getCurrentInputConnection();
		// //Log.d("Here", String.valueOf(mConnection));
		autotext = new Autotext();
		curOper = new CursorOperator(mConnection);
		state = 0L;
	}

	// //////////////////////////////////////////////////////////////////////////
	@Override
	public void onStartInputView(EditorInfo info, boolean restarting) {
		// //Log.d("Here", "onStartInputView()");
		super.onStartInputView(info, restarting);
		
		clipboard = (ClipboardManager) this.getSystemService(CLIPBOARD_SERVICE);//���ϵͳ������

		// ��ʼ������λ�ã�Ҳ�ɾݴ�֪����ǰ�༭�����ǰ�Ѿ��ж��ٸ��ַ��ˡ�
		mEditInfo = this.getCurrentInputEditorInfo();
		//����EditorInfo��ƥ��				
		if(mEditInfo.inputType == InputType.TYPE_CLASS_NUMBER || mEditInfo.inputType == InputType.TYPE_CLASS_PHONE || mEditInfo.inputType == InputType.TYPE_CLASS_DATETIME){
			state |= HandleMetaKey.META_ALT_LOCKED;
			handleStatusIcon(HandleMetaKey.getMetaState(state));
		}else if(mEditInfo.inputType == InputType.TYPE_CLASS_TEXT && (mEditInfo.inputType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0){
			state |= HandleMetaKey.META_CAP_LOCKED;
			handleStatusIcon(HandleMetaKey.getMetaState(state));
		}else{
			// ��ʾ���뷨��ͼ��
			this.showStatusIcon(R.drawable.status_normal);// ��ʾͼ��
			curStatusIcon = CurrentStatusIcon.NORMAL;
		}
		
		mStart = mEditInfo.initialSelStart;
		mEnd = mEditInfo.initialSelEnd;
		if (mStart == -1 || mEnd == -1) {// ���û��ȡ��ֵ������ΪĬ�ϵģ�
			mStart = 0;
			mEnd = 0;
		}
		// //Log.d("Here", "inimStart=" + String.valueOf(mStart) + "|inimEnd=" +
		// String.valueOf(mEnd));

		

		// ��ȡĬ�ϵ�����ʿ�
		methodItemList = dboper.loadMethodsData();
		// �����û�дʿ⣬�����ѵ���
		if (methodItemList.size() == 0) {
			Toast.makeText(this, this.getString(R.string.msg6), TOASTDURATION).show();
			return;
		}

		for (int i = 0; i < methodItemList.size(); i++) {
			MethodItem item = methodItemList.get(i);
			if (item.getIsDefault() == MethodItem.DEFAULT)
				selectedMethodPostion = i;
		}
		defaultMethodId = methodItemList.get(selectedMethodPostion).getId();
		Toast.makeText(this, methodItemList.get(selectedMethodPostion).getName(), TOASTDURATION).show();

		maxInputLength = dboper.getMaxInputLength(defaultMethodId);

	}

	// ///////////////////////////////////////////////////////////////////
	@Override
	public void onFinishInputView(boolean finishingInput) {
		// //Log.d("Here", "onFinishInputView()");
		this.hideStatusIcon();
		// �ָ���ʼֵ����������һ���༭���ڲſ���ȡ����ȷ��EditorInfo�ĳ�ʼ���λ�á�
		currentCursorEnd = -1;
		currentCursorStart = -1;

		super.onFinishInputView(finishingInput);
	}

	// /////////////////////////////////////////////////////////////////////////
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		mConnection.finishComposingText();
		//mConnection.beginBatchEdit();
		mConnection.endBatchEdit();// ������������䣬Ҫ��ȻEditText���ܻ���뵽BatchEditģʽ�����ἰʱ����onSelectionupdate���������¹����Ϣ���Ӷ�����

		// Log.d("Here",
		// "onKeyDown()" + "|Type=" +
		// String.valueOf(event.getKeyCharacterMap().getKeyboardType()) +
		// "|Mode="
		// + String.valueOf(event.getKeyCharacterMap().getModifierBehavior()) +
		// "|keycode=" + KeyEvent.keyCodeToString(keyCode)
		// + "|metastate=" +
		// String.valueOf(Integer.toBinaryString(event.getMetaState())) +
		// "|repeatcount="
		// + String.valueOf(event.getRepeatCount()) + "|flag=" +
		// String.valueOf(Integer.toBinaryString(event.getFlags())));

		// �������ֹ��ܼ��¼�
		// //Log.d("Here", "EventMetaState=" +
		// Integer.toBinaryString(event.getMetaState()));
		// //Log.d("Here", "state=" + Long.toBinaryString(state));
		state = HandleMetaKey.handleKeyDown(state, keyCode, event);
		//Log.d("Here", "downhandl=" + Long.toBinaryString(state));
		mMetaState = event.getMetaState() | HandleMetaKey.getMetaState(state);
		setMetaKeyStatus(mMetaState);
		//Log.d("Here", "ListenerMetaState=" +
		//		Integer.toBinaryString(HandleMetaKey.getMetaState(state)));
		//Log.d("Here", "FinalState=" + Integer.toBinaryString(mMetaState));
		if (keyCode != KeyEvent.KEYCODE_SHIFT_LEFT && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT && keyCode != KeyEvent.KEYCODE_ALT_LEFT
				&& keyCode != KeyEvent.KEYCODE_ALT_RIGHT && keyCode != KeyEvent.KEYCODE_SYM) {
			state = HandleMetaKey.adjustMetaAfterKeypress(state);
		}
		handleStatusIcon(mMetaState);

		if (currentCursorStart != -1) {// ˵����ǰ����λ�ò��ǳ�ʼλ�ã��Ǿ�ʹ��
			mStart = currentCursorStart;// �Ѳ�����ʼʱ�Ĺ��λ�ñ�������
			mEnd = currentCursorEnd;// �Ѳ�����ʼʱ�Ĺ��λ�ñ�������
		}		
		
		if (keyCode == ConstantList.SUBSTITUTION_TRIGGER) {// ���������滻�ַ�	
			//����Ƕ̰���������׼�������滻
			mBeforeSubString = "";// �滻ǰ���ı�
			mAfterSubString = new StringBuilder();// �滻����ı�

			if (mStart != mEnd) {// ����ѡ��ģʽ
				mConnection.commitText("", 1);
				mConnection.setSelection(mStart, mStart);
				mEnd = mStart;
			}

			// ���mBeforeSubString��Ϊ�գ�˵��֮ǰ���滻����ô���Ⱦ���Ҫ�жϣ����ڹ���Ƿ��������ϴη����滻�Ľ�β����
			if (!TextUtils.isEmpty(autotext.beforeString)) {
				// ȡ���ǰ��Ӧ���ȵ��ַ�������ȡ��ͷ
				String rawInput = mConnection.getTextBeforeCursor(autotext.beforeString.length(), 0).toString();
				if (mEnd == autotext.end && rawInput.equals(autotext.beforeString)) {
					mConnection.commitText(" ", 1);
					mConnection.setSelection(mEnd + 1, mEnd + 1);
					autotext.clear();
					return true;
				}
			}
			// ������������ڷ����滻����ô�Ϳ�ʼ���������滻�Ĺ���
			CharSequence candidateInput = mConnection.getTextBeforeCursor(maxInputLength + 1, 0);
			if (candidateInput.length() < 1) {

				mConnection.commitText(" ", 1);
				mConnection.setSelection(mEnd + 1, mEnd + 1);
				return true;
			}
			char c;
			for (offsetBefore = 1; offsetBefore <= candidateInput.length(); offsetBefore++) {// �ӵ�ǰλ�ÿ�ʼ��ǰ��
				c = candidateInput.charAt(candidateInput.length() - offsetBefore);
				// //Log.d("Here", "c=" + c);
				if (c == ConstantList.SUBSTITUTION_SEPERRATOR) {// ����ҵ����滻�ָ���
					break;
				}
			}
			offsetBefore--;
			candidateInput = candidateInput.subSequence(candidateInput.length() - offsetBefore, candidateInput.length());
			// //Log.d("Here", "candidateInput=" + candidateInput + "|");

			String rawAutotext = dboper.searchAutotext("autotext" + defaultMethodId, candidateInput.toString());// �ڿ��в����滻��
			if (rawAutotext == null) {// ���û���ҵ��滻��
				mConnection.commitText(" ", 1);
				//���û���ҵ��滻��ֻ���ʾһ��
				//Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE); 
				//vibrator.vibrate(50);				
				return true;
			} else {// ����ҵ����滻��
				// ��ʼɨ���滻��
				int macroBnumber = 0;// ���ڼ�¼%B������ĸ���
				for (int i = 0; i < rawAutotext.length(); i++) {
					c = rawAutotext.charAt(i);
					// ���ɨ�赽��������ַ�%
					if (c == ConstantList.MACRO_ESCAPECHARACTER) {
						c = (i + 1 < rawAutotext.length()) ? rawAutotext.charAt(i + 1) : ConstantList.MACRO_ESCAPECHARACTER;// '%'���������һ���ַ�������͵�������ͨ�ַ�
						switch (c) {
						case ConstantList.MACRO_DELETEBACK://��ǰ��ɾ��һ���ַ�
							if (mAfterSubString.length() != 0) {
								mAfterSubString.deleteCharAt(mAfterSubString.length() - 1);
							} else {
								offsetBefore++;
							}
							break;
						case ConstantList.MACRO_DELETEWORD:// ɾ�����滻�ĵ���
							offsetBefore += autotext.end - autotext.start;
							break;
						case ConstantList.MACRO_DELETEFORWARD://�ں���ɾ��һ���ַ�
							macroBnumber++;
							//һ���к�%B��ʱ�򣬶���ʾ�����룬�ֻ���һ��
							//Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE); 
							//vibrator.vibrate(50);							
							break;
						case ConstantList.MACRO_DATE:// date
							String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis()));
							mAfterSubString.append(date);
							break;
						case ConstantList.MACRO_LONGDATE:// date
							String datetime = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(System.currentTimeMillis()));
							mAfterSubString.append(datetime);
							break;
						case ConstantList.MACRO_TIME:// time
							String time = new SimpleDateFormat("HH:mm").format(new Date(System.currentTimeMillis()));
							mAfterSubString.append(time);
							break;
						case ConstantList.MACRO_ESCAPECHARACTER:// for char '%'
							mAfterSubString.append(c);
							break;
						default:
							// mAfterSubString.append(ConstantList.MACRO_ESCAPECHARACTER);
							// mAfterSubString.append(c);
						}
						i++;
					} else {// ����ͨ�ַ�
						mAfterSubString.append(c);
					}
				}

				// ���������滻����
				// ���ȣ�����ǰ���ɨ���������Ҫ���滻���ı�
				offsetAfter = macroBnumber <= 1 ? 0 : macroBnumber - 1;// ��Ϊ���滹��һ��������Ŀո���Ҫ���ǵ�
				offsetAfter = mConnection.getTextAfterCursor(offsetAfter, 0).length();
				offsetBefore = mConnection.getTextBeforeCursor(offsetBefore, 0).length();
				mBeforeSubString = mConnection.getTextBeforeCursor(offsetBefore, 0).toString()
						+ mConnection.getTextAfterCursor(offsetAfter, 0).toString();

				// �ڶ�����¼�滻�����Ϣ�����ܷŵ������¼����ΪmAfterSubString��䣬������Ҫ���Ͽո�
				autotext.start = mStart - offsetBefore;
				autotext.end = autotext.start + mAfterSubString.length();
				autotext.beforeString = mBeforeSubString;
				autotext.afterString = mAfterSubString.toString();

				// ���������������滻���ı������Ƿ�Ҫ���Ͽո�
				mAfterSubString = macroBnumber == 0 ? mAfterSubString.append(ConstantList.SUBSTITUTION_SEPERRATOR) : mAfterSubString;

				// ���ģ�׼������֮�������滻��ͬʱǿ�Ƹ��¹��λ��

				mConnection.deleteSurroundingText(offsetBefore, offsetAfter);
				mConnection.commitText(mAfterSubString, 1);
				mConnection.setSelection(autotext.start + mAfterSubString.length(), autotext.start + mAfterSubString.length());

				return true;
			}
			// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		} else if (keyCode == ConstantList.SUBSTITUTION_TRIGGER_REVERSE) {// ���������滻���ַ�
			// //Log.d("Here", "SUBTRIGGER");

			if (mStart != mEnd) {// ����ѡ��ģʽ
				mConnection.setSelection(mStart, mStart);
				mUndoSubString = mConnection.getTextAfterCursor(Math.abs(mEnd - mStart), 0).toString();
				mConnection.deleteSurroundingText(0, Math.abs(mEnd - mStart));
				mEnd = mStart;
				return true;
			}

			// ���������滻�������ǣ�
			// 1���滻�鲻��Ϊ��
			// 2����ǰ���ǰ���ַ����滻�����ȫ��ͬ�����ҹ���λ�����滻��Ľ��������ͬ
			// �����Ƿ����滻����
			// ����1:�滻�鲻��Ϊ��
			if (TextUtils.isEmpty(autotext.afterString) || TextUtils.isEmpty(autotext.beforeString)) {
				mUndoSubString = mConnection.getTextBeforeCursor(1, 0).toString();
				mConnection.deleteSurroundingText(1, 0);
				//mStart = mStart - 1 < 0 ? 0 : mStart - 1;
				//mEnd = mStart;
				//mConnection.setSelection(mStart, mStart);
				return true;
			}
			// //Log.d("Here", "condition1");

			// ����2 ��ǰ���ǰ���ַ����滻�����ȫ��ͬ�����ҹ���λ�����滻��Ľ��������ͬ
			String rawInput = mConnection.getTextBeforeCursor(autotext.afterString.length(), 0).toString();
			if (mEnd != autotext.end || !rawInput.equals(autotext.afterString)) {
				mUndoSubString = mConnection.getTextBeforeCursor(1, 0).toString();
				mConnection.deleteSurroundingText(1, 0);
				//mStart = mStart - 1 < 0 ? 0 : mStart - 1;
				//mEnd = mStart;
				//mConnection.setSelection(mStart, mStart);
				return true;
			}
			// //Log.d("Here", "condition2");

			// ��ʼ�����滻

			mConnection.deleteSurroundingText(autotext.end - autotext.start, 0);
			mConnection.commitText(autotext.beforeString, 1);

			// ��¼�滻����Ϣ�ı仯����ǿ�Ƹ��¹��λ��
			autotext.start = mEnd - autotext.afterString.length();
			autotext.end = mEnd - autotext.afterString.length() + autotext.beforeString.length();
			mConnection.setSelection(autotext.end, autotext.end);

			return true;
			// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_SELECTALL) {
			// ȫѡ
			mMoveWhichEnd = 1;
			mConnection.setSelection(0, mEnd + curOper.getAfterLength());			
			//mConnection.performContextMenuAction(android.R.id.selectAll);
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_SELETETOHOME) {
			// ѡ��ͷ
			//mConnection.setSelection(0, mEnd);
			mConnection.setSelection(0, mEnd);
			mMoveWhichEnd = 0;//�������ǰѡ��ͷ���������ƶ���굱ȻӦ�����ƶ���ʼλ
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_SELETETOEND) {
			// ѡ��β			
			mConnection.setSelection(mStart, mEnd + curOper.getAfterLength());
			mMoveWhichEnd = 1;//�������ǰѡ��ͷ���������ƶ���굱ȻӦ�����ƶ�����λ
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_SELETEBACK) {
			// // ��ǰѡ
			if(mStart != mEnd && mMoveWhichEnd == 1){//�����괦��ѡ��״̬�����ұ��λ����Ӧ���ƶ�����λ
				mEnd = (mEnd - 1) < 0 ? 0 : mEnd - 1;
			}else{
				mStart = (mStart - 1) < 0 ? 0 : mStart - 1;
				mMoveWhichEnd = 0;
			}			
			mConnection.setSelection(mStart, mEnd);
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_SELETEFORWARD) {
			// // ����ѡ
			int totalLength = curOper.getAfterLength();
			if(mStart != mEnd && mMoveWhichEnd == 0){//�����괦��ѡ��״̬�����ұ��λ����Ӧ���ƶ���ʼλ
				mStart = (mStart + 1) > mEnd + totalLength ? mEnd + totalLength : currentCursorStart + 1;
			}else{
				mEnd = (mEnd + 1) > mEnd + totalLength ? mEnd + totalLength : currentCursorEnd + 1;
				mMoveWhichEnd = 1;
			}			
			mConnection.setSelection(mStart, mEnd);
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_MOVETOHOME) {
			// ����Ƶ�ͷ��OKKKKKKKKKKKKKKKKK
			//curOper.moveCursorTo(0, mStart, mEnd);
			mConnection.setSelection(0, 0);
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_MOVETOEND) {
			// ����Ƶ�β��OKKKKKKKKKKKKKKKKK
			int totalLength = mEnd + curOper.getAfterLength();
			//curOper.moveCursorTo(totalLength, mStart, mEnd);
			mConnection.setSelection(totalLength, totalLength);
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_MOVEBACK) {
			// ��ǰ��һ���ַ�OKKKKKKKKKKKKKKKKK
			int pos = mStart != mEnd ? mStart : mStart - 1;
			pos = pos < 0 ? 0 : pos;
			// Log.d("Here", "mStart=" + String.valueOf(mStart) + "|mEnd=" +
			// String.valueOf(mEnd));
			//curOper.moveCursorTo(pos, mStart, mEnd);
			mConnection.setSelection(pos, pos);
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_MOVEFORWARD) {
			// ������һ���ַ�OKKKKKKKKKKKKKKKKKK
			int totalLength = mEnd + curOper.getAfterLength();
			int pos = mStart != mEnd ? mEnd : mEnd + 1;
			pos = pos > totalLength ? totalLength : pos;
			//curOper.moveCursorTo(pos, mStart, mEnd);
			mConnection.setSelection(pos, pos);
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_DELETEALL) {
			// // ɾ��ȫ������OKKKKKKKKKKKKKK
			mConnection.setSelection(mStart, mStart);//��������ȷ����겻�ᴦ��ѡ��״̬
			int afterLength = curOper.getAfterLength();						
			mUndoSubString = mConnection.getTextBeforeCursor(mStart+1, 0).toString() + mConnection.getTextAfterCursor(afterLength, 0).toString();
			mConnection.deleteSurroundingText(mStart + 1, afterLength);
			mConnection.setSelection(0, 0);
			return true;
		}
		else if (isCtrlPressed && keyCode == ConstantList.EDIT_DELETETOHOME) {
			// ɾ����ͷOKKKKKKKKKKKKKKKKK
			mUndoSubString = mConnection.getTextBeforeCursor(mStart, 0).toString();
			mConnection.deleteSurroundingText(mStart, 0);
			mConnection.setSelection(0, 0);
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_DELETETOEND) {
			// ɾ����βOKKKKKKKKKKKKKKKKKKKK
			int afterLength = curOper.getAfterLength();
			mUndoSubString = mConnection.getTextAfterCursor(afterLength, 0).toString();
			mConnection.deleteSurroundingText(0, afterLength);
			mConnection.setSelection(mEnd, mEnd);
			return true;
		}
		else if (isCtrlPressed && keyCode == ConstantList.EDIT_DELETEFORWARD) {
			// ɾ������һ���ַ�
			mUndoSubString = mConnection.getTextAfterCursor(1, 0).toString();
			mConnection.deleteSurroundingText(0, 1);
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_UNDO) {
			// undo����
			if(!mUndoSubString.isEmpty()){
				mConnection.commitText(mUndoSubString, 1);
				mUndoSubString = "";
			}			
			return true;
		}
		else if (isCtrlPressed && keyCode == ConstantList.EDIT_COPY) {
			// ����OKKKKKKKKKKKKKKKKK
			if (mStart != mEnd) {
				clipboard = (ClipboardManager) this.getSystemService(CLIPBOARD_SERVICE);
				ClipData clip = ClipData.newPlainText("AutotextInputMethod", mConnection.getSelectedText(InputConnection.GET_TEXT_WITH_STYLES));
				clipboard.setPrimaryClip(clip);
				mConnection.setSelection(mEnd, mEnd);

				Toast.makeText(this, this.getString(R.string.copyed), TOASTDURATION).show();
			}
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_PASTE || isCtrlPressed && keyCode == ConstantList.EDIT_UNDO) {
			// ճ������
			clipboard = (ClipboardManager) this.getSystemService(CLIPBOARD_SERVICE);
			CharSequence pasteText = "";
			if (clipboard.hasPrimaryClip()) {// �����������������
				ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
				pasteText = item.getText();
				mConnection.commitText(pasteText, 1);
			}
			//mConnection.performContextMenuAction(android.R.id.paste);
			return true;
		} else if (isCtrlPressed && keyCode == ConstantList.EDIT_CUT) {
			// ����OKKKKKKKKKKKKKKKKKK
			if (mStart != mEnd) {
				//ClipboardManager clipboard = (ClipboardManager) this.getSystemService(CLIPBOARD_SERVICE);
				//ClipData clip = ClipData.newPlainText("AutotextInputMethod", mConnection.getSelectedText(InputConnection.GET_TEXT_WITH_STYLES));
				//clipboard.setPrimaryClip(clip);
				//mConnection.commitText("", 1);
				int mUndoStart = mStart;
				int mUndoEnd = mEnd;
				mConnection.setSelection(mStart, mStart);
				mUndoSubString = mConnection.getTextAfterCursor(Math.abs(mEnd - mStart), 0).toString();
				mConnection.setSelection(mUndoStart, mUndoEnd);
				mConnection.performContextMenuAction(android.R.id.cut);
			}
			return true;
		} else if (isAltPressed && keyCode == ConstantList.SWITCH_INPUTMETHOD) {
			// �л����뷨�Ŀ�ݼ�
//			InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
//			List<InputMethodInfo> inputMethodList = imm.getInputMethodList();//���ϵͳ�������뷨�б�
//			String curInputMethodId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);//��õ�ǰ���뷨��id
//			int listId = inputMethodList.indexOf(curInputMethodId);//���㵱ǰ���뷨���б����ǵڼ���
//			Log.d("Here", curInputMethodId + " is " +String.valueOf(listId));
//			
//			//�����ǰ���뷨�Ǳ����뷨��idΪ cn.queshw.autotextinputmethod/.AutotextInputMethod�����Ҳ������һ���ʿ���
//			if(curInputMethodId == ConstantList.METHODID && selectedMethodPostion != methodItemList.size()){
//				selectedMethodPostion++;
//				defaultMethodId = methodItemList.get(selectedMethodPostion).getId();
//				dboper.addOrSaveMethodItem(methodItemList.get(selectedMethodPostion).getName(), MethodItem.DEFAULT, defaultMethodId);
//				Toast.makeText(this, "AutoText:" + methodItemList.get(selectedMethodPostion).getName(), TOASTDURATION).show();
//			}else{//������������뷨��������һ�����뷨�л�
//				listId = listId + 1 > inputMethodList.size()? 0: listId + 1;
//				this.switchInputMethod(inputMethodList.get(listId).getId());
//				Toast.makeText(this, inputMethodList.get(listId).getServiceName() , TOASTDURATION).show();
//			}
			
			selectedMethodPostion = selectedMethodPostion + 1 < methodItemList.size() ? selectedMethodPostion + 1 : 0;
			defaultMethodId = methodItemList.get(selectedMethodPostion).getId();
			dboper.addOrSaveMethodItem(methodItemList.get(selectedMethodPostion).getName(), MethodItem.DEFAULT, defaultMethodId);
			Toast.makeText(this, methodItemList.get(selectedMethodPostion).getName(), TOASTDURATION).show();
			return true;
		}else if(isSymPressed){//��ʱʲô����������
			//Log.d("Here", "sym pressed there");
			//return false;
		}else if(keyCode == ConstantList.SUBSTITUTION_ENTER || keyCode == ConstantList.SUBSTITUTION_NUMPAD_ENTER){//�������س���
			mConnection.performEditorAction(mEditInfo.imeOptions&EditorInfo.IME_MASK_ACTION);
		}
		else {
			KeyCharacterMap kcm = event.getKeyCharacterMap();
			// //Log.d("Here", String.valueOf(kcm.getModifierBehavior()));
			if (kcm.isPrintingKey(keyCode)) {

				if (isCtrlPressed)// ˵�������ڿ������ģʽ��
					return true;
				
				char c;		
				if (event.getRepeatCount() == 0) {//�̰�
					c = (char) kcm.get(keyCode, mMetaState);
				} else if (event.getRepeatCount() == 1) {// ������д
					c = (char) kcm.get(keyCode, KeyEvent.META_CAPS_LOCK_ON);
					mConnection.deleteSurroundingText(1, 0);
				}  else {
					return true;
				}
				
				mConnection.commitText(String.valueOf(c), 1);
				return true;
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	

	// /////////////////////////////////////////////////////////////////////////
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		// Log.d("Here",
		// "onKeyUp()" + "|Type=" +
		// String.valueOf(event.getKeyCharacterMap().getKeyboardType()) +
		// "|Mode="
		// + String.valueOf(event.getKeyCharacterMap().getModifierBehavior()) +
		// "|keycode=" + KeyEvent.keyCodeToString(keyCode)
		// + "|metastate=" +
		// String.valueOf(Integer.toBinaryString(event.getMetaState())) +
		// "|repeatcount="
		// + String.valueOf(event.getRepeatCount()) + "|flag=" +
		// String.valueOf(Integer.toBinaryString(event.getFlags())));

		state = HandleMetaKey.handleKeyUp(state, keyCode, event);
		// Log.d("Here", "uphandled=" + Long.toBinaryString(state));
		mMetaState = HandleMetaKey.getMetaState(state) | event.getMetaState();
		handleStatusIcon(mMetaState);

		return super.onKeyUp(keyCode, event);
	}

	// ///////////////////////////////////////////////////////////////////////
	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
		// //Log.d("Here", "newSelS=" + String.valueOf(newSelStart) +
		// "|newSelE="
		// + String.valueOf(newSelEnd));
		// //Log.d("Here", "oldSelS=" + String.valueOf(oldSelStart) +
		// "|oldSelE="
		// + String.valueOf(oldSelEnd));
		currentCursorStart = newSelStart;
		currentCursorEnd = newSelEnd;

		super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
	}

	// /////////////////////////////////////////////
	private void setMetaKeyStatus(int mMetaState) {
		// Log.d("Here", "setMetaKeyStatus = " +
		// String.valueOf(Integer.toBinaryString(mMetaState)));

		if ((mMetaState & KeyEvent.META_ALT_ON) != 0) {// ����alt��
			// Log.d("Here", "alt true");
			isAltPressed = true;
		} else {
			// Log.d("Here", "alt false");
			isAltPressed = false;
		}
		if ((mMetaState & KeyEvent.META_SHIFT_LEFT_ON) != 0) {// ������shift�������ﵱ��ctrl��
			// Log.d("Here", "ctrl true");
			isCtrlPressed = true;
		} else {
			// Log.d("Here", "ctrl false");
			isCtrlPressed = false;
		}
		if ((mMetaState & KeyEvent.META_SHIFT_RIGHT_ON) != 0) {// ������shift��
			// Log.d("Here", "cap true");
			isCapPressed = true;
		} else {
			// Log.d("Here", "cap false");
			isCapPressed = false;
		}
		if ((mMetaState & KeyEvent.META_SYM_ON) != 0) {// ����sym��
			//Log.d("Here", "sym true");
			isSymPressed = true;
		} else {
			//Log.d("Here", "sym false");
			isSymPressed = false;
		}
	}

	// /////////////////////////////////////////////
	private void handleStatusIcon(int mMetaState) {
		// Log.d("Here", "handleStatusIcon = " +
		// String.valueOf(Integer.toBinaryString(mMetaState)));
		CurrentStatusIcon icon;
		if ((mMetaState & 0x100) != 0) {// cap locked
			icon = CurrentStatusIcon.CAP_LOCK;
		} else if ((mMetaState & KeyEvent.META_SHIFT_RIGHT_ON) != 0) {// cap on
			icon = CurrentStatusIcon.CAP_ON;
		} else if ((mMetaState & 0x200) != 0) {// alt locked
			icon = CurrentStatusIcon.ALT_LOCK;
		} else if ((mMetaState & KeyEvent.META_ALT_ON) != 0) {// alt on
			icon = CurrentStatusIcon.ALT_ON;
		} else if ((mMetaState & 0x400) != 0) {// sym locked
			icon = CurrentStatusIcon.SYM_LOCK;
		} else if ((mMetaState & KeyEvent.META_SYM_ON) != 0) {// sym on
			icon = CurrentStatusIcon.SYM_ON;
		} else if ((mMetaState & 0x800) != 0) {// ctrl locked
			icon = CurrentStatusIcon.NEWSIM_LOCK;
		} else if ((mMetaState & KeyEvent.META_SHIFT_LEFT_ON) != 0) {// ctrl on
			icon = CurrentStatusIcon.NEWSIM_ON;
		} else {
			icon = CurrentStatusIcon.NORMAL;
		}

		if (curStatusIcon != icon) {
			this.hideStatusIcon();
			this.showStatusIcon(icon.getIconId());
			curStatusIcon = icon;
		}
	}

	// ��ǰͼ��ö�ٱ��
	enum CurrentStatusIcon {
		NORMAL(R.drawable.status_normal), CAP_ON(R.drawable.status_cap), CAP_LOCK(R.drawable.status_cap_lock), ALT_ON(R.drawable.status_alt), ALT_LOCK(
				R.drawable.status_alt_lock), SYM_ON(R.drawable.status_sym), SYM_LOCK(R.drawable.status_sym_lock), NEWSIM_ON(R.drawable.status_ctrl), NEWSIM_LOCK(
				R.drawable.status_ctrl_lock);

		private int iconId;

		CurrentStatusIcon(int iconId) {
			this.iconId = iconId;
		}

		int getIconId() {
			return iconId;
		}
	}
}