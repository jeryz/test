package com.kaadas.lock.activity.device.wifilock.wifilist;

import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;

import com.blankj.utilcode.util.ConvertUtils;
import com.kaadas.lock.activity.choosewifi.WifiBean;
import com.kaadas.lock.utils.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 解析蓝牙发送的wifi列表数据
 *
 */
public class BleWifiListDataParser {

    private static final String TAG = "BleWifiListDataParser";
    private final ArrayList<WifiBean> mWifiSnList = new ArrayList<>();
    private final HashMap<Integer, WifiSnBean> mWifiHashMap = new HashMap<>();
    private WifiListDataCallback callback;
    Handler mHandler;
    private Runnable timeoutRunnable;

    public interface WifiListDataCallback {
        void onWifiData(ArrayList<WifiBean> data);
    }

    public BleWifiListDataParser(){
        HandlerThread handlerThread = new HandlerThread("WifiDataParser-Thread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    public void resetData(){
        mWifiSnList.clear();
        mWifiHashMap.clear();
        if(timeoutRunnable != null){
            mHandler.removeCallbacks(timeoutRunnable);
        }
        LogUtils.d(TAG,"reset wifiData " + mWifiSnList.size());
    }

    public synchronized void parseWifiListFromBle(byte[] payload){

        final int wifiTotalNum = payload[0]; //当前 WiFi 设备搜索到的热点总数
        final int index = payload[1]; //列表序号
        final int RSSI = payload[2]; //0x00-0x7F
        final int SSID_len = payload[3]; //SSID 总长度
        final int SSID_index = payload[4]; //包序号，第 N 包（从 0 开始）


        WifiSnBean wifiSnBean = mWifiHashMap.get(index);

        byte[] SSIDBytes = new byte[11];
        System.arraycopy(payload, 5, SSIDBytes, 0, SSIDBytes.length);

        final WifiSnBean.WifiSnBytesBean bytesBean = new WifiSnBean.WifiSnBytesBean(SSID_index, SSID_len);
        bytesBean.setBytes(SSIDBytes);
        List<WifiSnBean.WifiSnBytesBean> bytesBeans;
        if (wifiSnBean == null) {
            bytesBeans = new ArrayList<>();
            bytesBeans.add(bytesBean);
            wifiSnBean = new WifiSnBean();
            wifiSnBean.setRssi(RSSI);
            wifiSnBean.setWifiIndex(index);
            wifiSnBean.setWifiSnBytesBeans(bytesBeans);
            mWifiHashMap.put(index, wifiSnBean);
        } else {
            bytesBeans = wifiSnBean.getWifiSnBytesBeans();
            if (bytesBeans == null) {
                bytesBeans = new ArrayList<>();
            }
            bytesBeans.add(bytesBean);
            wifiSnBean.setWifiSnBytesBeans(bytesBeans);
        }

        //倒数第二数据
        if(index == wifiTotalNum -2){
            boolean isRemain = (bytesBean.getSnLenTotal() % 11 != 0);
            int lastIndex = bytesBean.getSnLenTotal() / 11;
            if (bytesBean.getIndex() == (isRemain ? lastIndex : lastIndex - 1)) {
                if(timeoutRunnable != null){
                    mHandler.removeCallbacks(timeoutRunnable);
                }
                timeoutRunnable = new Runnable() {
                    @Override
                    public void run() {
                        LogUtils.i(TAG,"--kaadas-- wifiData lost, postDelayed, total" + wifiTotalNum + "=?" + index);
                        handleLastData(wifiTotalNum, bytesBean);
                    }
                };
                mHandler.postDelayed(timeoutRunnable, 500);
                LogUtils.d(TAG,"--kaadas-- last WifiData postDelayed");
            }
            LogUtils.d(TAG,"--kaadas-- last two WifiData, total=" + wifiTotalNum + " index=" + index + " SSID_index=" + SSID_index);
        }

        //最后一个数据
        if (index == (wifiTotalNum - 1)) {
            LogUtils.d(TAG,"--kaadas-- last one WifiData, total=" + wifiTotalNum + " index=" + index + " SSID_index=" + SSID_index);
            handleLastData(wifiTotalNum, bytesBean);
        }
    }

    private synchronized void handleLastData(int total, WifiSnBean.WifiSnBytesBean bytesBean){
        boolean isRemain = (bytesBean.getSnLenTotal() % 11 != 0);
        int lastIndex = bytesBean.getSnLenTotal() / 11;
        if (bytesBean.getIndex() == (isRemain ? lastIndex : lastIndex - 1)) {
            if (!mWifiHashMap.isEmpty()) {
                mWifiSnList.clear();
                for (int i = 0; i < total; i++) {
                    WifiSnBean wifiSnBean = mWifiHashMap.get(i);
                    if (wifiSnBean != null) {
                        int rssi = wifiSnBean.getRssi();
                        //getWifiSn 有下标溢出bug
                        String wifiSn = wifiSnBean.getWifiSn();
                        if(TextUtils.isEmpty(wifiSn)){
                            continue;
                        }
                        int level = WifiManager.calculateSignalLevel(rssi, 5);
                        mWifiSnList.add(new WifiBean(level, wifiSn));
                    }
                }
            }

            if(callback != null){
                LogUtils.d(TAG,"--kaadas-- callback.onWifiData");
                callback.onWifiData(mWifiSnList);
            }
        }
    }

    public void setWifiListDataCallback(WifiListDataCallback callback){
        this.callback = callback;
    }
}
