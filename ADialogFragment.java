package com.example.test;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.FloatRange;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.collection.SparseArrayCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;


/**
 * Created by zjr on 2019/3/20.
 */
public class ADialogFragment extends DialogFragment {

    private volatile boolean isShowing;
    private Dialog dialog;
    private Builder mBuilder;
    private static int itemHeight = 45;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        dialog = new Dialog(getContext(), getTheme());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        if(mBuilder == null){
            return dialog;
        }

        window.setGravity(mBuilder.gravity);
        window.setWindowAnimations(mBuilder.animRes);
        WindowManager.LayoutParams attr = window.getAttributes();
        if(mBuilder.windowParams!=null){
            //attr.dimAmount
        }

        window.setAttributes(attr);
        dialog.setCanceledOnTouchOutside(mBuilder.outsideCancel);
        dialog.setCancelable(mBuilder.cancelable);

        initView(getContext());

        if (mBuilder.onDismissListener != null)
            dialog.setOnDismissListener(mBuilder.onDismissListener);

        if (mBuilder.onCreateListener != null) {
            mBuilder.onCreateListener.OnCreate(dialog, mBuilder.contentView);
        }

        return dialog;
    }

    private void initView(Context context) {
        FrameLayout dialogLayout = new FrameLayout(context);
        dialogLayout.setLayoutParams(new FrameLayout.LayoutParams(WRAP_CONTENT,WRAP_CONTENT));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mBuilder.contentView.getLayoutParams();
        if(params==null){
            mBuilder.setDialogScaleSize(0.5f,0);
            params = (FrameLayout.LayoutParams) mBuilder.contentView.getLayoutParams();
        }
        if(mBuilder.titleHolder!=null){
            params.topMargin = (int) (itemHeight*mBuilder.density);
            TextView titleView = new TextView(context);
            titleView.setText(mBuilder.titleHolder.text);
            titleView.setGravity(mBuilder.titleHolder.gravity);
            titleView.setTextSize(mBuilder.titleHolder.textSize);
            titleView.setTextColor(mBuilder.titleHolder.textColor);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(WRAP_CONTENT, (int) (itemHeight * mBuilder.density));
            p.gravity = mBuilder.titleHolder.layoutGravity;
            titleView.setLayoutParams(p);
            dialogLayout.addView(titleView);
        }
        int btnWidth = params.width>0?params.width:WRAP_CONTENT;
        if(mBuilder.cancelHolder !=null&&mBuilder.submitHolder !=null&&btnWidth>0){
            btnWidth /= 2;
        }
        if(mBuilder.cancelHolder !=null){
            params.bottomMargin = (int) (itemHeight*mBuilder.density);
            mBuilder.cancelHolder.width = btnWidth;
            mBuilder.cancelHolder.height = (int) (itemHeight * mBuilder.density);
            Button button = getButton(context,mBuilder.cancelHolder);
            button.setOnClickListener(new DismissClickProxy(this,mBuilder.cancelHolder.clickListener));
            dialogLayout.addView(button);
        }
        if(mBuilder.submitHolder !=null){
            params.bottomMargin = (int) (itemHeight*mBuilder.density);
            mBuilder.submitHolder.width = btnWidth;
            mBuilder.submitHolder.height = (int) (itemHeight * mBuilder.density);

            Button button = getButton(context,mBuilder.submitHolder);
            button.setOnClickListener(new DismissClickProxy(this,mBuilder.submitHolder.clickListener));
            dialogLayout.addView(button);
        }

        if(dialogLayout.getChildCount()>0){
            dialogLayout.setBackground(getBgDrawable());
            dialogLayout.addView(mBuilder.contentView,params);
            dialog.setContentView(dialogLayout);
        }else{
            mBuilder.contentView.setBackground(getBgDrawable());
            dialog.setContentView(mBuilder.contentView);
        }
    }

    private Drawable getBgDrawable() {
        float r = mBuilder.density * mBuilder.bgCornerRadii;
        GradientDrawable drawable = getShapeDrawable(mBuilder.backgroundColor,r,r,r,r);
        return drawable;
    }

    private GradientDrawable getShapeDrawable(int color, float tl, float tr, float br, float bl) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float density = mBuilder.density;
        //top-left, top-right, bottom-right, bottom-left
        drawable.setCornerRadii(new float[]{density*tl,density*tl,density*tr,density*tr,density*br,density*br,density*bl,density*bl});
        return drawable;
    }

    Button getButton(Context context, Builder.ViewHolder holder){
        Button view = new Button(context);

//        Button view = null;
//        try {
//            Constructor constructor = holder.viewClass.getConstructor(Context.class);
//            view = (Button) constructor.newInstance(context);
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (java.lang.InstantiationException e) {
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//        }

        view.setText(holder.text);
        view.setGravity(holder.gravity);
        view.setTextSize(holder.textSize);
        view.setTextColor(holder.textColor);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(holder.width, holder.height);
        params.gravity = holder.layoutGravity;
        view.setLayoutParams(params);
        setViewBackground(holder.bgColor,holder.bgSelector,holder.cornerRadii, view);

        return view;
    }

    private void setViewBackground(int bgColor, int[] bgSelector, float[] radii, Button view) {
        if(bgSelector!=null){
            StateListDrawable stateListDrawable = new StateListDrawable();
            Drawable drawable = getShapeDrawable(bgSelector[0],radii[0] , radii[1], radii[2], radii[3]);
            Drawable pdrawable = getShapeDrawable(bgSelector[1], radii[0] , radii[1], radii[2], radii[3]);
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed},pdrawable);
            stateListDrawable.addState(new int[]{android.R.attr.state_focused},pdrawable);
            stateListDrawable.addState(new int[]{},drawable);
            view.setBackground(stateListDrawable);
        }else {
            view.setBackgroundColor(bgColor>0?bgColor:mBuilder.backgroundColor);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //点击事件
        int size = mBuilder.clickListeners.size();
        for (int i = 0; i < size; i++) {
            int key = mBuilder.clickListeners.keyAt(i);
            View.OnClickListener value = mBuilder.clickListeners.valueAt(i);
            View findView = mBuilder.contentView.findViewById(key);
            if (findView != null) {
                findView.setOnClickListener(value);
            }
        }
        //dismiss事件
        if(mBuilder.dismissClickListeners !=null){
            int size1 = mBuilder.dismissClickListeners.size();
            for (int i = 0; i < size1; i++) {
                int id = mBuilder.dismissClickListeners.get(i);
                View.OnClickListener listener = mBuilder.clickListeners.get(id);
                View view = mBuilder.contentView.findViewById(id);
                view.setOnClickListener(new DismissClickProxy(this,listener));
            }
        }
        //EditText编辑监听
        int size1 = mBuilder.editListeners.size();
        for (int i = 0; i < size1; i++) {
            int key = mBuilder.editListeners.keyAt(i);
            TextWatcher value = mBuilder.editListeners.valueAt(i);
            View findView = mBuilder.contentView.findViewById(key);
            if (findView instanceof EditText) {
                EditText editText = (EditText) findView;
                editText.addTextChangedListener(value);
            }
        }

    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }

    private void setBuilder(Builder builder) {
        mBuilder = builder;
    }

    public void show(FragmentActivity context) {
        Lifecycle.State currentState = context.getLifecycle().getCurrentState();
        if (!Lifecycle.State.RESUMED.equals(currentState)) {//不可见状态不显示
            return;
        }
        if (context.isFinishing() || isAdded() || isVisible()) {
            return;
        }
        show(context.getSupportFragmentManager(), ADialogFragment.class.getSimpleName());
    }

    public boolean isShowing() {
        return isShowing;
    }

    @Override
    public void dismiss() {
        isShowing = false;
        dialog = null;
        super.dismiss();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        isShowing = true;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        isShowing = false;
    }

    public View getContentView() {
        return mBuilder.contentView;
    }

    public String getTextById(@IdRes int viewId) {
        TextView textView = getView().findViewById(viewId);
        return textView.getText().toString().trim();
    }
    static class DismissClickProxy implements View.OnClickListener{

        private final View.OnClickListener listener;
        private final ADialogFragment dialogFragment;

        public DismissClickProxy(ADialogFragment dialogFragment, View.OnClickListener listener) {
            this.listener = listener;
            this.dialogFragment = dialogFragment;
        }

        @Override
        public void onClick(View v) {
            if(listener!=null)listener.onClick(v);
            if(dialogFragment!=null)dialogFragment.dismiss();
        }
    }

    public static class Builder {
        private int widthPixels;
        private int heightPixels;
        private float density;
        private View contentView;
        private OnCreateListener onCreateListener;
        private int backgroundColor = Color.WHITE;
        private boolean outsideCancel = true;
        private boolean cancelable = true;
        private boolean fullScreen = false;
        private SparseArrayCompat<View.OnClickListener> clickListeners = new SparseArrayCompat<>();
        private SparseArrayCompat<TextWatcher> editListeners = new SparseArrayCompat<>();
        private SparseIntArray dismissClickListeners = new SparseIntArray();
        private int gravity = Gravity.CENTER;
        private int animRes;
        private int bgCornerRadii = 8;
        private DialogInterface.OnDismissListener onDismissListener;
        private WindowManager.LayoutParams windowParams;
        private ViewHolder titleHolder;
        private ViewHolder cancelHolder;
        private ViewHolder submitHolder;

        public Builder(Context context, @LayoutRes int layoutId) {
            this(LayoutInflater.from(context).inflate(layoutId, null, false));
        }

        public Builder(View view) {
            this.contentView = view;
            DisplayMetrics metrics = view.getContext().getResources().getDisplayMetrics();
            density = metrics.density;
            widthPixels = metrics.widthPixels;
            heightPixels = metrics.heightPixels;
            setDialogScaleSize(0.6f,0);
        }

        public Builder setDialogScaleSize(@FloatRange(from = 0.0, to = 1.0) float w, @FloatRange(from = 0.0, to = 1.0) float h) {
            int min = Math.min(widthPixels, heightPixels);
            int width = (int) (widthPixels * w);
            int height = (int) (heightPixels * h);
            width = width == 0 ? WRAP_CONTENT : width;
            height = height == 0 ? WRAP_CONTENT : height;
            setDialogSize(width,height);
            return this;
        }

        public Builder setDialogSize(int w, int h) {
            ViewGroup.LayoutParams params = contentView.getLayoutParams();
            if (params == null) {
                params = new FrameLayout.LayoutParams(w, h);
            } else {
                if(w>0&&h>0){
                    params.width = w;
                    params.height = h;
                }
            }
            contentView.setLayoutParams(params);
            return this;
        }

        public Builder setWindowLayoutParams(WindowManager.LayoutParams params){
            windowParams = params;
            return this;
        }

        public Builder setGravity(int gravity) {
            this.gravity = gravity;
            return this;
        }

        public Builder setBgCorner(int radii) {
            bgCornerRadii = radii;
            return this;
        }

        public Builder setOnDismissListener(DialogInterface.OnDismissListener listener) {
            onDismissListener = listener;
            return this;
        }

        public Builder setDismissClickListener(@IdRes int... viewId) {
            for (int i = 0; i < viewId.length; i++) {
                dismissClickListeners.append(dismissClickListeners.size(), viewId[i]);
            }
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Builder setFullScreen(boolean fullScreen) {
            this.fullScreen = fullScreen;
            return this;
        }

        public Builder setAnimationStyle(@StyleRes int animRes) {
            this.animRes = animRes;
            return this;
        }

        public Builder setBackgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public Builder setOutsideCancel(boolean outsideCancel) {
            this.outsideCancel = outsideCancel;
            return this;
        }

        public Builder setOnClickListener(@IdRes int viewId, View.OnClickListener onClickListener) {
            clickListeners.put(viewId, onClickListener);
            return this;
        }

        public Builder setOnClickListener(@IdRes int[] viewIds, View.OnClickListener onClickListener) {
            for (int i = 0; i < viewIds.length; i++) {
                clickListeners.put(viewIds[i], onClickListener);
            }
            return this;
        }

        public Builder setOnEditListener(@IdRes int viewId, TextWatcher listener) {
            editListeners.put(viewId, listener);
            return this;
        }

        public Builder setText(@IdRes int viewId, String text) {
            TextView textView = contentView.findViewById(viewId);
            textView.setText(text);
            return this;
        }

        public Builder setImage(@IdRes int viewId, @DrawableRes int id) {
            ImageView imageView = contentView.findViewById(viewId);
            imageView.setImageResource(id);
            return this;
        }

        public Builder setImage(@IdRes int viewId, Drawable drawable) {
            ImageView imageView = contentView.findViewById(viewId);
            imageView.setImageDrawable(drawable);
            return this;
        }

        public Builder addView(@IdRes int viewGroupId, View childView) {
            ViewGroup viewGroup = contentView.findViewById(viewGroupId);
            viewGroup.addView(childView);
            return this;
        }

        public Builder setVisibility(@IdRes int viewId, int visibility) {
            View child = contentView.findViewById(viewId);
            child.setVisibility(visibility);
            return this;
        }

        public Builder setOnCreateListener(OnCreateListener listener) {
            onCreateListener = listener;
            return this;
        }

        public String getTextById(@IdRes int viewId) {
            TextView textView = contentView.findViewById(viewId);
            return textView.getText().toString().trim();
        }

        public <T> T getView(@IdRes int viewId) {
            return (T) contentView.findViewById(viewId);
        }

        public Builder setDefaultButton(String leftText, String rightText, int pressedColor){
            int[] colors = {backgroundColor, pressedColor};
            if(!TextUtils.isEmpty(leftText)){
                cancelHolder = new ViewHolder(Button.class);
                cancelHolder.text = leftText;
                cancelHolder.bgSelector = colors;
                cancelHolder.layoutGravity = Gravity.LEFT| Gravity.BOTTOM;
                cancelHolder.cornerRadii = new float[]{0,0,0,density*bgCornerRadii};
            }
            if(!TextUtils.isEmpty(rightText)){
                submitHolder = new ViewHolder(Button.class);
                submitHolder.text = rightText;
                submitHolder.bgSelector = colors;
                submitHolder.layoutGravity = Gravity.RIGHT| Gravity.BOTTOM;
                submitHolder.cornerRadii = new float[]{0,0,density*bgCornerRadii,0};
            }
            return this;
        }

        public Builder setDefaultButtonListener(View.OnClickListener cancel, View.OnClickListener submit){
            if(cancelHolder!=null){
                cancelHolder.clickListener = cancel;
            }
            if(submitHolder!=null){
                submitHolder.clickListener = submit;
            }
            return this;
        }

        public Builder setDefaultTitle(String text, int gravity){
            titleHolder = new ViewHolder(TextView.class);
            titleHolder.layoutGravity = Gravity.CENTER| Gravity.TOP;
            titleHolder.text = text;
            titleHolder.gravity = gravity;
            return this;
        }

        public ADialogFragment build() {
            if(fullScreen){
                setDialogScaleSize(1,1);
            }

            ADialogFragment dialogFragment = new ADialogFragment();
            dialogFragment.setBuilder(this);
            return dialogFragment;
        }

        public class ViewHolder{
            public Class viewClass;
            public int height;
            public int width;
            public String text;
            public View.OnClickListener clickListener;
            public int textColor = 0xff333333;
            public int[] bgSelector;//normal,passed两个参数
            public int textSize = 16;
            public int bgColor;
            public float[] cornerRadii;//4个参数top-left, top-right, bottom-right, bottom-left
            public int gravity = Gravity.CENTER;
            public int layoutGravity = Gravity.CENTER;

            public ViewHolder(Class viewClass) {
                this.viewClass = viewClass;
            }
        }
    }

    public interface OnCreateListener {
        void OnCreate(Dialog dialog, View view);
    }
}
