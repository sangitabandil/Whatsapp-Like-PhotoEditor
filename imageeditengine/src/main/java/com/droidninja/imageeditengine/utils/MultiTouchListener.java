package com.droidninja.imageeditengine.utils;

import android.graphics.Rect;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.droidninja.imageeditengine.views.ViewTouchListener;

/**
 * Created by Burhanuddin Rashid on 18/01/2017.
 */
public class MultiTouchListener implements OnTouchListener {

  private static final int INVALID_POINTER_ID = -1;
  private final GestureDetector mGestureListener;
  private final ViewTouchListener viewTouchListener;
  private final boolean isRotateEnabled = true;
  private final boolean isTranslateEnabled = true;
  private final boolean isScaleEnabled = true;
  private final float minimumScale = 0.5f;
  private final float maximumScale = 10.0f;
  private int mActivePointerId = INVALID_POINTER_ID;
  private float mPrevX, mPrevY, mPrevRawX, mPrevRawY;
  private final ScaleGestureDetector mScaleGestureDetector;

  private final int[] location = new int[2];
  private final Rect outRect;
  private final View deleteView;
  private final ImageView photoEditImageView;
  private final RelativeLayout parentView;

  private OnMultiTouchListener onMultiTouchListener;
  private OnGestureControl mOnGestureControl;
  private final boolean mIsTextPinchZoomable;
  private View currentView;

  public MultiTouchListener(@Nullable View deleteView, RelativeLayout parentView,
                            ImageView photoEditImageView, boolean isTextPinchZoomable,
                            ViewTouchListener viewTouchListener) {
    mIsTextPinchZoomable = isTextPinchZoomable;
    this.viewTouchListener = viewTouchListener;
    mScaleGestureDetector = new ScaleGestureDetector(new ScaleGestureListener());
    mGestureListener = new GestureDetector(new GestureListener());
    this.deleteView = deleteView;
    this.parentView = parentView;
    this.photoEditImageView = photoEditImageView;
    //this.mOnPhotoEditorListener = onPhotoEditorListener;
    if (deleteView != null) {
      outRect = new Rect(deleteView.getLeft(), deleteView.getTop(), deleteView.getRight(),
          deleteView.getBottom());
    } else {
      outRect = new Rect(0, 0, 0, 0);
    }
  }

  private static float adjustAngle(float degrees) {
    if (degrees > 180.0f) {
      degrees -= 360.0f;
    } else if (degrees < -180.0f) {
      degrees += 360.0f;
    }

    return degrees;
  }

  private static void move(View view, TransformInfo info) {
    computeRenderOffset(view, info.pivotX, info.pivotY);
    adjustTranslation(view, info.deltaX, info.deltaY);

    float scale = view.getScaleX() * info.deltaScale;
    scale = Math.max(info.minimumScale, Math.min(info.maximumScale, scale));
    view.setScaleX(scale);
    view.setScaleY(scale);

    float rotation = adjustAngle(view.getRotation() + info.deltaAngle);
    view.setRotation(rotation);
  }

  private static void adjustTranslation(View view, float deltaX, float deltaY) {
    float[] deltaVector = { deltaX, deltaY };
    view.getMatrix().mapVectors(deltaVector);
    view.setTranslationX(view.getTranslationX() + deltaVector[0]);
    view.setTranslationY(view.getTranslationY() + deltaVector[1]);
  }

  private static void computeRenderOffset(View view, float pivotX, float pivotY) {
    if (view.getPivotX() == pivotX && view.getPivotY() == pivotY) {
      return;
    }

    float[] prevPoint = { 0.0f, 0.0f };
    view.getMatrix().mapPoints(prevPoint);

    view.setPivotX(pivotX);
    view.setPivotY(pivotY);

    float[] currPoint = { 0.0f, 0.0f };
    view.getMatrix().mapPoints(currPoint);

    float offsetX = currPoint[0] - prevPoint[0];
    float offsetY = currPoint[1] - prevPoint[1];

    view.setTranslationX(view.getTranslationX() - offsetX);
    view.setTranslationY(view.getTranslationY() - offsetY);
  }

  @Override public boolean onTouch(View view, MotionEvent event) {
    this.currentView = view;
    mScaleGestureDetector.onTouchEvent(view, event);
    mGestureListener.onTouchEvent(event);

    if (!isTranslateEnabled) {
      return true;
    }

    int action = event.getAction();

    int x = (int) event.getRawX();
    int y = (int) event.getRawY();

    switch (action & event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        mPrevX = event.getX();
        mPrevY = event.getY();
        mPrevRawX = event.getRawX();
        mPrevRawY = event.getRawY();
        mActivePointerId = event.getPointerId(0);
        //if (deleteView != null) {
        //  deleteView.setVisibility(View.VISIBLE);
        //}
        view.bringToFront();
        firePhotoEditorSDKListener(view, true);
        break;
      case MotionEvent.ACTION_MOVE:
        int pointerIndexMove = event.findPointerIndex(mActivePointerId);
        if (pointerIndexMove != -1) {
          float currX = event.getX(pointerIndexMove);
          float currY = event.getY(pointerIndexMove);
          if (!mScaleGestureDetector.isInProgress()) {
            adjustTranslation(view, currX - mPrevX, currY - mPrevY);
          }

          if (deleteView != null) {
            deleteView.setSelected(
                isViewInBounds(deleteView, (int) view.getX(), (int) view.getY(), false));
          }
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        mActivePointerId = INVALID_POINTER_ID;
        break;
      case MotionEvent.ACTION_UP:
        mActivePointerId = INVALID_POINTER_ID;
        if (deleteView != null && isViewInBounds(deleteView, (int) view.getX(), (int) view.getY(),
            false)) {
          if (onMultiTouchListener != null) {
            onMultiTouchListener.onRemoveViewListener(view);
          }
        } else if (!isViewInBounds(photoEditImageView, x, y, true)) {
          view.animate().translationY(0).translationY(0);
        }
        //if (deleteView != null) {
        //  deleteView.setVisibility(View.GONE);
        //}
        firePhotoEditorSDKListener(view, false);
               /* float mCurrentCancelX = event.getRawX();
                float mCurrentCancelY = event.getRawY();
                if (mCurrentCancelX == mPrevRawX || mCurrentCancelY == mPrevRawY) {
                    if (view instanceof FrameLayout) {
                        TextView text = (TextView) ((FrameLayout) view).getChildAt(1);
                        if (onMultiTouchListener != null) {
                            onMultiTouchListener.onEditTextClickListener(
                                    text.getText().toString(), text.getCurrentTextColor());
                        }
                        if (mOnPhotoEditorListener != null) {
                            mOnPhotoEditorListener.onEditTextChangeListener(
                                    text.getText().toString(), text.getCurrentTextColor());
                        }
                    }
                }*/
        break;
      case MotionEvent.ACTION_POINTER_UP:
        int pointerIndexPointerUp = (action & MotionEvent.ACTION_POINTER_INDEX_MASK)
            >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        int pointerId = event.getPointerId(pointerIndexPointerUp);
        if (pointerId == mActivePointerId) {
          int newPointerIndex = pointerIndexPointerUp == 0 ? 1 : 0;
          mPrevX = event.getX(newPointerIndex);
          mPrevY = event.getY(newPointerIndex);
          mActivePointerId = event.getPointerId(newPointerIndex);
        }
        break;
    }
    return true;
  }

  private void firePhotoEditorSDKListener(View view, boolean isStart) {
    if (view instanceof TextView || view instanceof ImageView) {

      if (viewTouchListener != null) {
        if (isStart) {
          viewTouchListener.onStartViewChangeListener(view);
        } else {
          viewTouchListener.onStopViewChangeListener(view);
        }
      }
    }
  }

  private boolean isViewInBounds(View view, int x, int y, boolean getLocationFromScreen) {
    view.getDrawingRect(outRect);
    view.getLocationOnScreen(location);
    Log.i("outRect:", outRect.toString());
    if (getLocationFromScreen) {
      outRect.offset(location[0], location[1]);
    }
    Log.i("viewbOunds:", outRect.toString() + " x:" + x + " y:" + y);
    return outRect.contains(x, y);
  }

  public void setOnMultiTouchListener(OnMultiTouchListener onMultiTouchListener) {
    this.onMultiTouchListener = onMultiTouchListener;
  }

  private class ScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

    private float mPivotX;
    private float mPivotY;
    private final Vector2D mPrevSpanVector = new Vector2D();

    @Override public boolean onScaleBegin(View view, ScaleGestureDetector detector) {
      mPivotX = detector.getFocusX();
      mPivotY = detector.getFocusY();
      mPrevSpanVector.set(detector.getCurrentSpanVector());
      return mIsTextPinchZoomable;
    }

    @Override public boolean onScale(View view, ScaleGestureDetector detector) {
      TransformInfo info = new TransformInfo();
      info.deltaScale = isScaleEnabled ? detector.getScaleFactor() : 1.0f;
      info.deltaAngle =
          isRotateEnabled ? Vector2D.getAngle(mPrevSpanVector, detector.getCurrentSpanVector())
              : 0.0f;
      info.deltaX = isTranslateEnabled ? detector.getFocusX() - mPivotX : 0.0f;
      info.deltaY = isTranslateEnabled ? detector.getFocusY() - mPivotY : 0.0f;
      info.pivotX = mPivotX;
      info.pivotY = mPivotY;
      info.minimumScale = minimumScale;
      info.maximumScale = maximumScale;
      move(view, info);
      return !mIsTextPinchZoomable;
    }
  }

  private class TransformInfo {
    float deltaX;
    float deltaY;
    float deltaScale;
    float deltaAngle;
    float pivotX;
    float pivotY;
    float minimumScale;
    float maximumScale;
  }

  public interface OnMultiTouchListener {

    void onRemoveViewListener(View removedView);
  }

  public interface OnGestureControl {
    void onClick(View currentView);

    void onLongClick();
  }

  public void setOnGestureControl(OnGestureControl onGestureControl) {
    mOnGestureControl = onGestureControl;
  }

  private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override public boolean onSingleTapUp(MotionEvent e) {
      if (mOnGestureControl != null) {
        mOnGestureControl.onClick(currentView);
      }
      return true;
    }

    @Override public void onLongPress(MotionEvent e) {
      super.onLongPress(e);
      if (mOnGestureControl != null) {
        mOnGestureControl.onLongClick();
      }
    }
  }
}