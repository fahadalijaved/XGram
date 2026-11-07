/*
 * This file is a part of XGram, a fork of Telegram X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextWrapper;


/**
 * XGram: a checklist (TdApi.MessageChecklist), shown like official Telegram's: the title, how many tasks are done, then
 * each task with a round check you tap to mark it done or not done (when the checklist allows it), who did it in a
 * group, and "Add a task" when tasks can be added. Changes go to Telegram (markChecklistTasksAsDone, addChecklistTasks)
 * and come back as a content update.
 */
public class TGMessageChecklist extends TGMessage {
  private static final float CHECK_SIZE = 22f, CHECK_GAP = 12f, ROW_PADDING = 8f, ADD_ROW_HEIGHT = 40f;

  private TdApi.Checklist list;
  private @Nullable TextWrapper titleText;
  private @Nullable Text statusText, addText;
  private TaskEntry[] tasks = new TaskEntry[0];
  private int contentWidth, contentHeight, tasksTop, addRowTop;

  private static final class TaskEntry {
    final TdApi.ChecklistTask task;
    final TextWrapper text;
    @Nullable Text byText;
    int top, height;

    TaskEntry (TdApi.ChecklistTask task, TextWrapper text) {
      this.task = task;
      this.text = text;
    }
  }

  public TGMessageChecklist (MessagesManager manager, TdApi.Message msg, TdApi.MessageChecklist content) {
    super(manager, msg);
    setList(content.list);
  }

  private void setList (TdApi.Checklist list) {
    this.list = list;
    this.titleText = list.title != null && list.title.text != null && !list.title.text.isEmpty() ?
      new TextWrapper(tdlib, list.title, getBiggerTextStyleProvider(), getTextColorSet(), openParameters(), (wrapper, text, specificMedia) -> invalidateTextMediaReceiver(text, specificMedia)) : null;
    TaskEntry[] entries = new TaskEntry[list.tasks.length];
    for (int i = 0; i < entries.length; i++) {
      TdApi.ChecklistTask task = list.tasks[i];
      entries[i] = new TaskEntry(task, new TextWrapper(tdlib, task.text, getTextStyleProvider(), getTextColorSet(), openParameters(), (wrapper, text, specificMedia) -> invalidateTextMediaReceiver(text, specificMedia)));
    }
    this.tasks = entries;
  }

  private int doneCount () {
    int count = 0;
    for (TdApi.ChecklistTask task : list.tasks) {
      if (task.completionDate != 0) {
        count++;
      }
    }
    return count;
  }

  private boolean isGroup () {
    return msg.chatId < 0 && !tdlib.isChannel(msg.chatId);
  }

  @Override
  protected void buildContent (int maxWidth) {
    contentWidth = maxWidth;
    int y = 0;
    if (titleText != null) {
      titleText.prepare(maxWidth);
      y += titleText.getHeight() + Screen.dp(4f);
    }
    final String status = Lang.getString(isGroup() || tdlib.isChannel(msg.chatId) ? R.string.XgramChecklistGroup : R.string.XgramChecklist) +
      " · " + Lang.getString(R.string.XgramChecklistDone, doneCount(), list.tasks.length);
    statusText = new Text.Builder(status, maxWidth, Paints.robotoStyleProvider(12f), getDecentColorSet()).singleLine().build();
    y += statusText.getHeight() + Screen.dp(8f);
    tasksTop = y;
    final int textLeft = Screen.dp(CHECK_SIZE + CHECK_GAP);
    final int textWidth = Math.max(Screen.dp(40f), maxWidth - textLeft);
    for (TaskEntry entry : tasks) {
      entry.top = y;
      entry.text.prepare(textWidth);
      int height = Math.max(Screen.dp(CHECK_SIZE), entry.text.getHeight());
      entry.byText = null;
      if (entry.task.completionDate != 0 && entry.task.completedBy != null && (isGroup() || tdlib.isChannel(msg.chatId))) {
        String name = tdlib.senderName(entry.task.completedBy, true);
        if (name != null && !name.isEmpty()) {
          entry.byText = new Text.Builder(Lang.getString(R.string.XgramChecklistDoneBy, name), textWidth, Paints.robotoStyleProvider(12f), getDecentColorSet()).singleLine().build();
          height += entry.byText.getHeight() + Screen.dp(2f);
        }
      }
      entry.height = height + Screen.dp(ROW_PADDING) * 2;
      y += entry.height;
    }
    addRowTop = y;
    if (list.canAddTasks) {
      addText = new Text.Builder(Lang.getString(R.string.XgramChecklistAdd), textWidth, getTextStyleProvider(), getLinkColorSet()).singleLine().allBold(true).build();
      y += Screen.dp(ADD_ROW_HEIGHT);
    } else {
      addText = null;
    }
    contentHeight = y;
  }

  @Override
  protected int getContentWidth () {
    return contentWidth;
  }

  @Override
  protected int getContentHeight () {
    return contentHeight;
  }

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    final int count = tasks.length + 1;
    final int idOffset = Integer.MAX_VALUE / (count + 1);
    if (titleText != null) {
      titleText.requestMedia(textMediaReceiver, 0, idOffset);
    } else {
      textMediaReceiver.clearReceiversRange(0, idOffset);
    }
    int key = 0;
    for (TaskEntry entry : tasks) {
      key += idOffset;
      entry.text.requestMedia(textMediaReceiver, key, idOffset);
    }
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    lastStartX = startX;
    lastStartY = startY;
    if (titleText != null) {
      titleText.draw(c, startX, startX + contentWidth, 0, startY, null, 1f, view.getTextMediaReceiver());
    }
    if (statusText != null) {
      statusText.draw(c, startX, startY + (titleText != null ? titleText.getHeight() + Screen.dp(4f) : 0));
    }
    final int accent = getProgressColor();
    final int decent = getDecentColor();
    final RectF rect = Paints.getRectF();
    final int size = Screen.dp(CHECK_SIZE);
    final int textLeft = startX + Screen.dp(CHECK_SIZE + CHECK_GAP);
    for (TaskEntry entry : tasks) {
      final int rowTop = startY + entry.top + Screen.dp(ROW_PADDING);
      final float cx = startX + size / 2f, cy = rowTop + size / 2f;
      final boolean done = entry.task.completionDate != 0;
      if (done) {
        c.drawCircle(cx, cy, size / 2f, Paints.fillingPaint(accent));
        // the check mark
        final Paint check = Paints.getProgressPaint(0xffffffff, Screen.dp(2f)); // a shared paint: fetched for each use
        final android.graphics.Path path = checkPath;
        path.rewind();
        path.moveTo(cx - size * .22f, cy + size * .02f);
        path.lineTo(cx - size * .05f, cy + size * .18f);
        path.lineTo(cx + size * .24f, cy - size * .16f);
        c.drawPath(path, check);
      } else {
        rect.set(cx - size / 2f + Screen.dp(1f), cy - size / 2f + Screen.dp(1f), cx + size / 2f - Screen.dp(1f), cy + size / 2f - Screen.dp(1f));
        c.drawArc(rect, 0, 360, false, Paints.getProgressPaint(decent, Screen.dp(2f)));
      }
      final int textTop = rowTop + Math.max(0, (size - entry.text.getLineHeight()) / 2);
      entry.text.draw(c, textLeft, textLeft + (contentWidth - Screen.dp(CHECK_SIZE + CHECK_GAP)), 0, textTop, null, done ? .7f : 1f, view.getTextMediaReceiver());
      if (entry.byText != null) {
        entry.byText.draw(c, textLeft, textTop + entry.text.getHeight() + Screen.dp(2f));
      }
    }
    if (addText != null) {
      final int top = startY + addRowTop;
      final float cy = top + Screen.dp(ADD_ROW_HEIGHT) / 2f;
      final float cx = startX + size / 2f;
      final Paint plus = Paints.getProgressPaint(accent, Screen.dp(2f));
      c.drawLine(cx - size * .3f, cy, cx + size * .3f, cy, plus);
      c.drawLine(cx, cy - size * .3f, cx, cy + size * .3f, plus);
      addText.draw(c, textLeft, (int) (cy - addText.getHeight() / 2f));
    }
  }

  // Touches

  private int lastStartX, lastStartY;
  private final android.graphics.Path checkPath = new android.graphics.Path();
  private int touchTask = -1;
  private boolean touchAdd;
  private float touchX, touchY;

  private int taskAt (float x, float y) {
    if (x < lastStartX || x > lastStartX + contentWidth) {
      return -1;
    }
    for (int i = 0; i < tasks.length; i++) {
      final int top = lastStartY + tasks[i].top;
      if (y >= top && y < top + tasks[i].height) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        touchX = e.getX();
        touchY = e.getY();
        touchTask = list.canMarkTasksAsDone ? taskAt(e.getX(), e.getY()) : -1;
        touchAdd = addText != null && e.getX() >= lastStartX && e.getX() < lastStartX + contentWidth &&
          e.getY() >= lastStartY + addRowTop && e.getY() < lastStartY + addRowTop + Screen.dp(ADD_ROW_HEIGHT);
        if (touchTask != -1 || touchAdd) {
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        final int slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        if (Math.abs(e.getX() - touchX) > slop || Math.abs(e.getY() - touchY) > slop) {
          touchTask = -1;
          touchAdd = false;
        }
        if (touchTask != -1 || touchAdd) {
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (touchTask != -1) {
          toggleTask(tasks[touchTask].task);
          touchTask = -1;
          return true;
        }
        if (touchAdd) {
          touchAdd = false;
          addTask();
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        touchTask = -1;
        touchAdd = false;
        break;
      }
    }
    return super.onTouchEvent(view, e);
  }

  private void toggleTask (TdApi.ChecklistTask task) {
    final boolean done = task.completionDate != 0;
    tdlib.send(new TdApi.MarkChecklistTasksAsDone(msg.chatId, msg.id,
      done ? new int[0] : new int[] {task.id},
      done ? new int[] {task.id} : new int[0]
    ), (ok, error) -> {
      if (error != null) {
        runOnUiThreadOptional(() -> org.thunderdog.challegram.tool.UI.showError(error));
      }
    });
  }

  private void addTask () {
    controller().openInputAlert(Lang.getString(R.string.XgramChecklistAdd), Lang.getString(R.string.XgramChecklistTask), R.string.Done, R.string.Cancel, null, (inputView, result) -> {
      final String text = result != null ? result.trim() : "";
      if (text.isEmpty()) {
        return false;
      }
      int nextId = 1;
      for (TdApi.ChecklistTask task : list.tasks) {
        nextId = Math.max(nextId, task.id + 1);
      }
      tdlib.send(new TdApi.AddChecklistTasks(msg.chatId, msg.id, new TdApi.InputChecklistTask[] {
        new TdApi.InputChecklistTask(nextId, new TdApi.FormattedText(text, new TdApi.TextEntity[0]))
      }), (ok, error) -> {
        if (error != null) {
          runOnUiThreadOptional(() -> org.thunderdog.challegram.tool.UI.showError(error));
        }
      });
      return true;
    }, true);
  }

  // Updates

  @Override
  protected boolean onMessageContentChanged (TdApi.Message message, TdApi.MessageContent oldContent, TdApi.MessageContent newContent, boolean isBottomMessage) {
    if (newContent.getConstructor() == TdApi.MessageChecklist.CONSTRUCTOR) {
      setList(((TdApi.MessageChecklist) newContent).list);
      rebuildAndUpdateContent();
      invalidateTextMediaReceiver();
      return true;
    }
    return false;
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    if (newContent.getConstructor() == TdApi.MessageChecklist.CONSTRUCTOR) {
      setList(((TdApi.MessageChecklist) newContent).list);
      rebuildAndUpdateContent();
      invalidateTextMediaReceiver();
      return true;
    }
    return false;
  }
}
