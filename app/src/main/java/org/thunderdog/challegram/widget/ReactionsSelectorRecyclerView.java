package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessageOptionsPagerController;
import org.thunderdog.challegram.util.text.Counter;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class ReactionsSelectorRecyclerView extends RecyclerView {
  private final GradientDrawable gradientDrawableRight;
  private final MessageOptionsPagerController.State state;
  private boolean needDrawBorderGradient;
  private ReactionsAdapter adapter;

  public ReactionsSelectorRecyclerView (@NonNull Context context, MessageOptionsPagerController.State state) {
    super(context);
    this.state = state;
    this.gradientDrawableRight = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ 0, lastColor = Theme.backgroundColor() });

    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false) {
      @Override
      protected boolean isLayoutRTL () {
        return false;
      }
    };

    setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    setPadding(Screen.dp(9), Screen.dp(7), Screen.dp(9), Screen.dp(7));
    setClipToPadding(false);
    setHasFixedSize(true);
    addItemDecoration(new ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
        outRect.set(Screen.dp(-1), 0, Screen.dp(-1), 0);
      }
    });

    setLayoutManager(linearLayoutManager);
    setAdapter(adapter = new ReactionsAdapter(getContext(), state));
  }

  public void setNeedDrawBorderGradient (boolean needDrawBorderGradient) {
    this.needDrawBorderGradient = needDrawBorderGradient;
    this.invalidate();
  }

  // XGram: Material 3 Expressive: the reactions sit in a surfaceContainerHigh pill row
  private final android.graphics.Path xgramPill = new android.graphics.Path();
  private final android.graphics.RectF xgramPillRect = new android.graphics.RectF();

  @Override
  protected void dispatchDraw (Canvas canvas) {
    final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    int xgramPillRight = getMeasuredWidth();
    if (xgramM3) {
      int inset = Screen.dp(4f);
      int right = getMeasuredWidth() - inset;
      final int childCount = getChildCount();
      if (childCount > 0 && computeHorizontalScrollRange() <= computeHorizontalScrollExtent()) {
        // XGram: a short reaction list (it does not scroll) gets a pill that ends after its last reaction, with the
        // gap it has at the start (the pill 4dp in, the first reaction at the 9dp padding), not a long empty tail
        final View last = getChildAt(childCount - 1);
        if (last != null) {
          right = Math.min(right, (int) (last.getRight() + last.getTranslationX()) + Screen.dp(5f));
        }
      }
      xgramPillRight = right + inset;
      xgramPillRect.set(inset, inset, right, getMeasuredHeight() - inset);
      float r = xgramPillRect.height() / 2f;
      xgramPill.rewind();
      xgramPill.addRoundRect(xgramPillRect, r, r, android.graphics.Path.Direction.CW);
      canvas.drawPath(xgramPill, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH)));
      canvas.save();
      canvas.clipPath(xgramPill);
    }
    super.dispatchDraw(canvas);
    if (!needDrawBorderGradient) {
      if (xgramM3) {
        canvas.restore();
      }
      return;
    }

    checkGradients();
    float s = computeHorizontalScrollRange() - computeHorizontalScrollOffset() - computeHorizontalScrollExtent();
    int alpha = (int) (MathUtils.clamp(s / Screen.dp(20f)) * 255);

    gradientDrawableRight.setAlpha(alpha);
    gradientDrawableRight.setBounds(xgramPillRight - Screen.dp(35), 0, xgramPillRight, getMeasuredHeight()); // XGram: the pill's end
    gradientDrawableRight.draw(canvas);
    if (xgramM3) {
      canvas.restore();
    }
  }

  @Override
  public void onScrolled (int dx, int dy) {
    super.onScrolled(dx, dy);
    if (needDrawBorderGradient) {
      invalidate();
    }
  }

  private int lastColor;

  private void checkGradients () {
    int color = com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH) : Theme.backgroundColor(); // XGram: fade into the pill
    if (color != lastColor) {
      gradientDrawableRight.setColors(new int[]{ 0, lastColor = color });
    }
  }

  private static class ReactionView extends FrameLayoutFix {
    public StickerSmallView stickerView;
    private Counter counter;
    private boolean chosen;
    private boolean useCounter;
    private TGStickerObj centerAnimationSicker;
    private RectF rectF;

    public ReactionView (Context context) {
      super(context);
      stickerView = new StickerSmallView(context, 0) {
        @Override
        public boolean dispatchTouchEvent (MotionEvent event) {
          return false;
        }
      };
      stickerView.setLayoutParams(newParams(Screen.dp(40), Screen.dp(40), Gravity.LEFT | Gravity.CENTER_VERTICAL));
      addView(stickerView);

      rectF = new RectF();
      counter = new Counter.Builder()
        .noBackground()
        .textColor(com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? ColorId.fillingActiveContent : ColorId.fillingPositiveContent, ColorId.text, ColorId.text)
        .textSize(13f)
        .allBold(false)
        .callback(this)
        .build();
    }

    public void setReaction (TGReaction reaction, TdApi.MessageReaction messageReaction, boolean useCounter) {
      this.useCounter = useCounter;
      this.chosen = messageReaction.isChosen;
      this.centerAnimationSicker = reaction.newCenterAnimationSicker();
      this.playAnimation();
      stickerView.setSticker(centerAnimationSicker);
      if (useCounter) {
        counter.setCount(messageReaction.totalCount, !messageReaction.isChosen, false);
      }
      requestLayout();
    }

    public void playAnimation () {
      if (centerAnimationSicker != null && centerAnimationSicker.getPreviewAnimation() != null && !centerAnimationSicker.isCustomReaction()) {
        centerAnimationSicker.getPreviewAnimation().setPlayOnce(true);
        centerAnimationSicker.getPreviewAnimation().setLooped(false);
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = Screen.dp(40f);
      int padding = Screen.dp(1);
      if (useCounter) {
        width += counter.getScaledWidth(Screen.dp(6));
      }

      rectF.set(0, Screen.dp(1), width, Screen.dp(39));

      super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Screen.dp(40), MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      if (chosen) {
        c.drawRoundRect(rectF, Screen.dp(19), Screen.dp(19), Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER) : Theme.getColor(ColorId.fillingPositive))); // XGram M3E: selected = secondaryContainer
      }
      if (useCounter) {
        counter.draw(c, Screen.dp(36), getMeasuredHeight() / 2f, Gravity.LEFT, 1f);
      }
      super.dispatchDraw(c);
    }
  }

  private static class ReactionHolder extends RecyclerView.ViewHolder {
    public ReactionHolder (@NonNull View itemView) {
      super(itemView);
    }

    public static ReactionHolder create (Context context, Tdlib tdlib) {
      ReactionView reactionView = new ReactionView(context);
      reactionView.stickerView.init(tdlib);
      reactionView.setId(R.id.btn_reactionSelector);
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        // XGram: Material 3 Expressive: a 40dp state layer circle under each reaction
        reactionView.setBackground(com.fahadali.xgram.appearance.XgramExpressiveC.iconButtonRipple(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE), com.fahadali.xgram.appearance.XgramExpressiveC.ICON_BUTTON_BOX));
      }
      //reactionView.setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));
      return new ReactionHolder(reactionView);
    }
  }

  private static class ReactionsAdapter extends RecyclerView.Adapter<ReactionHolder> {
    private final Tdlib tdlib;
    private final Context context;
    private final TGMessage message;
    private final TdApi.AvailableReaction[] reactions;
    private final MessageOptionsPagerController.State state;

    ReactionsAdapter (Context context, MessageOptionsPagerController.State state) {
      this.context = context;
      this.tdlib = state.tdlib;
      this.message = state.message;
      this.state = state;
      this.reactions = state.availableReactions;
    }

    @NonNull
    @Override
    public ReactionHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return ReactionHolder.create(context, tdlib);
    }

    @Override
    public void onBindViewHolder (@NonNull ReactionHolder holder, int position) {
      TdApi.ReactionType reactionType = reactions[position].type;
      TGReaction reaction = tdlib.getReaction(reactionType);
      TdApi.MessageReaction tdReaction = message.getMessageReactions().getTdMessageReaction(reactionType);
      ReactionView view = (ReactionView) holder.itemView;
      if (reaction == null) return;

      final boolean needUseCounter = (message.isChannel() || !message.canGetAddedReactions()) && !message.useReactionBubbles();
      view.setReaction(reaction, tdReaction, needUseCounter);
      view.setOnClickListener((v) -> {
        state.onReactionClickListener.onReactionClick(v, reaction, false);
      });
      view.setOnLongClickListener((v) -> {
        state.onReactionClickListener.onReactionClick(v, reaction, true);
        return true;
      });
    }

    @Override
    public int getItemCount () {
      return reactions != null ? reactions.length : 0;
    }

    @Override
    public void onViewAttachedToWindow (ReactionHolder holder) {
      ((ReactionView) holder.itemView).stickerView.attach();
      ((ReactionView) holder.itemView).playAnimation();
    }

    @Override
    public void onViewDetachedFromWindow (ReactionHolder holder) {
      ((ReactionView) holder.itemView).stickerView.detach();
    }

    @Override
    public void onViewRecycled (ReactionHolder holder) {
      ((ReactionView) holder.itemView).stickerView.performDestroy();
    }
  }
}
