package com.kaadas.lock.activity.device.wifilock.wifilist;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kaadas.lock.R;
import com.kaadas.lock.activity.choosewifi.WifiBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WIFI列表弹窗
 */
public class WifiListDialogFragment extends DialogFragment {

    private View.OnClickListener refreshClickListener;
    private OnItemClickListener itemClickListener;
    private WifiListAdapter wifiListAdapter;
    private final List<WifiBean> wifiBeanList = new ArrayList<>();
    private Dialog dialog;
    private int dialogWidth;
    private RecyclerView recyclerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout dialogLayout = new FrameLayout(inflater.getContext());
        View view = inflater.inflate(R.layout.dialog_wifi_list, dialogLayout, false);
        dialogLayout.addView(view);
        dialogWidth = view.getLayoutParams().width;
        return dialogLayout;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        return dialog;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(dialogWidth <= 0){
            int widthPixels = dialog.getContext().getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout((int) (widthPixels * 0.8f), WindowManager.LayoutParams.WRAP_CONTENT);
        }

        dialog.findViewById(R.id.ivClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        dialog.findViewById(R.id.tvRefresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(refreshClickListener != null){
                    refreshClickListener.onClick(v);
                }
            }
        });

        recyclerView = dialog.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(dialog.getContext()));
        wifiListAdapter = new WifiListAdapter();
        recyclerView.setAdapter(wifiListAdapter);

    }

    public void setOnRefreshClick(View.OnClickListener clickListener){
        refreshClickListener = clickListener;
    }

    public void setOnItemClick(OnItemClickListener clickListener){
        itemClickListener = clickListener;
    }

    public void updateList(List<WifiBean> list){
        if(list == null || list.size() == 0){
            return;
        }
        wifiBeanList.clear();
        wifiBeanList.addAll(list);
        if(wifiListAdapter != null){
            wifiListAdapter.notifyDataSetChanged();
            recyclerView.scrollToPosition(0);
        }
    }

    public void show(FragmentActivity context){
        if(isResumed() || context.isFinishing() || isAdded() || isVisible()){
            return;
        }
        super.showNow(context.getSupportFragmentManager(), WifiListDialogFragment.class.getSimpleName());
    }

    public class WifiListAdapter extends RecyclerView.Adapter<WifiListAdapter.ItemHolder>{

        @NonNull
        @Override
        public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemHolder itemHolder = new ItemHolder(parent);
            itemHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(itemClickListener != null){
                        int position = itemHolder.getBindingAdapterPosition();
                        itemClickListener.OnItemClick(wifiBeanList.get(position), position);
                        dismiss();
                    }
                }
            });
            return itemHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull ItemHolder holder, int position) {
            WifiBean wifiBean = wifiBeanList.get(position);
            holder.tvSSID.setText(wifiBean.name);
            int resId = R.mipmap.wifi4;
            switch (wifiBean.ImageId){
                case 1:
                    resId = R.mipmap.wifi1;
                    break;
                case 2:
                    resId = R.mipmap.wifi2;
                    break;
                case 3:
                    resId = R.mipmap.wifi3;
                    break;
                case 4:
                    resId = R.mipmap.wifi4;
                    break;
            }
            Drawable drawable = ResourcesCompat.getDrawable(getResources(), resId, null);
            holder.tvSSID.setCompoundDrawablesWithIntrinsicBounds(null,null, drawable,null);
        }

        @Override
        public int getItemCount() {
            return wifiBeanList.size();
        }

        public class ItemHolder extends RecyclerView.ViewHolder{
            public TextView tvSSID;
            public ItemHolder(@NonNull View parent) {
                super(LayoutInflater.from(parent.getContext()).inflate(R.layout.dialog_wifi_list_item, null));
                tvSSID = itemView.findViewById(R.id.tvSSID);
            }
        }
    }

    public interface OnItemClickListener{
        void OnItemClick(WifiBean data, int position);
    }
}
