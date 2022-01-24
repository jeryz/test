package com.kaadas.lock.biometric;

import static android.security.keystore.KeyProperties.BLOCK_MODE_GCM;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.kaadas.lock.utils.SPUtils;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * author : zhangjierui
 * time   : 2022/1/22
 * desc   : 生物识别验证，BiometricPrompt目前只支持指纹识别验证，国内系统不支持人脸验证
 *
 * 注意：使用BiometricPrompt需要添加依赖包和权限
 * implementation "androidx.biometric:biometric:1.1.0"
 * <uses-permission android:name="android.permission.USE_BIOMETRIC"/>
 */
public class BiometricLoginManager {

    public static final String TAG = "BiometricLoginManager";
    private volatile static BiometricLoginManager instance;
    private final int KEY_SIZE = 256;
    private final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private final String KEY_NAME = "Kaadas_SecretKey_Id50";
    private final String BIOMETRIC_CIPHER_IV = "biometric_cipher_iv";
    private final String BIOMETRIC_CIPHER_DATA = "biometric_cipher_data";
    private String biometricToken = "welcome_to_Kaadas";
    private BiometricPrompt biometricPrompt;
    private String promptTitle = "";
    private String promptSubtitle = "";
    private String promptDescription = "";
    private String promptButtonText = "";

    public interface OnAuthenticationCallback{
        void onAuthenticationSucceeded();
        default void onAuthenticationFailed(){};
        default void onNegativeButtonClick(){};
        default void onAuthenticationError(int errorCode, CharSequence errString){};
    }

    private BiometricLoginManager(){
        promptTitle = "请按压指纹感应区域验证指纹";
        promptSubtitle = "";
        promptDescription = "";
        promptButtonText = "使用密码登录";
        //java.util.UUID.randomUUID().toString()
    }

    public static BiometricLoginManager getInstance(){
        if(instance == null){
            synchronized (BiometricLoginManager.class){
                if(instance == null){
                    instance = new BiometricLoginManager();
                }
            }
        }
        return instance;
    }

    public void setUserToken(@NonNull String token){
        biometricToken = token;
    }

    public void setPromptInfo(String title, String subTitle, String description, String buttonText){
        promptTitle = title;
        promptSubtitle = subTitle;
        promptDescription = description;
        promptButtonText = buttonText;
    }

    public void authenticate(FragmentActivity activity, OnAuthenticationCallback callback){
        if(activity == null) return;
        authenticate(activity, null, callback);
    }

    public void authenticate(Fragment fragment, OnAuthenticationCallback callback){
        if(fragment == null) return;
        authenticate(null, fragment, callback);
    }

    private void authenticate(FragmentActivity activity, Fragment fragment, final OnAuthenticationCallback callback){
        Context appContext;
        if(activity != null){
            appContext = activity.getApplicationContext();
        }else{
            appContext = fragment.getActivity();
        }

        int canAuthenticate = BiometricManager.from(appContext)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        //三星手机有注册人脸但没注册指纹 也会返回BIOMETRIC_SUCCESS
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            if(canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED){
                if(callback != null) callback.onAuthenticationError(canAuthenticate, "未注册生物特征，无法验证");
            }
            Log.e(TAG,"can't authenticate, " + canAuthenticate);
            //不支持用生物识别验证
            return;
        }

        BiometricPrompt.PromptInfo promptInfo = getPromptInfo();
        CiphertextWrapper cipherData = getCipherData();
        BiometricPrompt.CryptoObject cryptoObject = getCryptoObject(cipherData);
        biometricPrompt = getBiometricPrompt(activity, fragment, cipherData, callback);
        if(cryptoObject != null){
            biometricPrompt.authenticate(promptInfo, cryptoObject);
        }else{
            biometricPrompt.authenticate(promptInfo);
        }
    }

    public boolean isDeviceSecure(Context appContext){
        //如果设置了PIN、图案或密码，则返回true
        KeyguardManager keyguardManager = appContext.getSystemService(KeyguardManager.class);
        return keyguardManager.isDeviceSecure();
    }

    private BiometricPrompt.CryptoObject getCryptoObject(final CiphertextWrapper cipherData){
        Cipher cipher;
        if(cipherData != null && cipherData.iv != null){
            cipher = getCipher(KEY_NAME, cipherData.iv);
        }else{
            cipher = getCipher(KEY_NAME, null);
        }
        if(cipher == null){
            Log.e(TAG,"cipher is null");
            return null;
        }
        return new BiometricPrompt.CryptoObject(cipher);
    }

    private BiometricPrompt.PromptInfo getPromptInfo() {
        return new BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setSubtitle(promptSubtitle)
                .setDescription(promptDescription)
                .setConfirmationRequired(false)
                .setNegativeButtonText(promptButtonText)
                .build();
    }

    private BiometricPrompt getBiometricPrompt(FragmentActivity activity, Fragment fragment,
                                               final CiphertextWrapper cipherData, final OnAuthenticationCallback callback) {

        BiometricPrompt.AuthenticationCallback authenticationCallback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                //11未注册任何指纹
                //7 操作过于频繁，请稍后再试
                //5 指纹操作已取消。
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Log.i(TAG, "onAuthenticationError ERROR_NEGATIVE_BUTTON");
                    if(callback != null) callback.onNegativeButtonClick();
                    return;
                }

                Log.w(TAG, "onAuthenticationError " + errorCode + " " + errString);
                if(callback != null) callback.onAuthenticationError(errorCode, errString);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                Log.i(TAG, "Authentication was successful");
                BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                if(cryptoObject != null && cryptoObject.getCipher() != null){
                     if(cipherData != null && cipherData.ciphertext != null){
                        String decryptResult = decryptData(cipherData.ciphertext, cryptoObject.getCipher());
                        if(!biometricToken.equals(decryptResult)){
                            Log.i(TAG, "decrypt failed");
                            onAuthenticationFailed();
                            return;
                        }
                         Log.i(TAG, "decrypt successful");
                     }else{
                        saveCipherData(encryptData(biometricToken, cryptoObject.getCipher()));
                        Log.i(TAG, "encrypt and save");
                    }

                    if(callback != null) callback.onAuthenticationSucceeded();
                }
            }

            @Override
            public void onAuthenticationFailed() {
                if(callback != null) callback.onAuthenticationFailed();
                Log.w(TAG, "Authentication failed for an unknown reason");
            }
        };

        if(fragment != null){
            return new BiometricPrompt(fragment, ContextCompat.getMainExecutor(fragment.getActivity()), authenticationCallback);
        }else {
            return new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), authenticationCallback);
        }
    }

    public void cancelAuthenticate(){
        if(biometricPrompt != null){
            biometricPrompt.cancelAuthentication();
        }
    }

    private Cipher getCipher(String keyName, byte[] iv){
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey secretKey = getSecretKey(keyName);
            if(cipher == null || secretKey == null){
                return null;
            }

            if(iv != null){ //decrypt
                cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
                Log.i(TAG, "decrypt cipher");
            }else{  //encrypt
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                Log.i(TAG, "encrypt cipher");
            }
            return cipher;
        }catch (KeyPermanentlyInvalidatedException e){
            Log.w(TAG, "fingerprint changed: " + e.getMessage());
        }catch (UnrecoverableKeyException e){
            Log.w(TAG, "credentials changed: " + e.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private SecretKey getSecretKey(String keyName) throws UnrecoverableKeyException {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            Key key = keyStore.getKey(keyName, null);
            if(key != null){
                //已存在秘钥
                return (SecretKey)key;
            }

            //生成新秘钥
            int purposes = KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT;
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(keyName, purposes);
            builder.setBlockModes(BLOCK_MODE_GCM)
                    .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE)
                    .setUserAuthenticationRequired(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(false);
            }
            KeyGenParameterSpec keyGenParameterSpec = builder.build();

            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            keyGenerator.init(keyGenParameterSpec);
            return keyGenerator.generateKey();

        }catch (UnrecoverableKeyException e){
            throw new UnrecoverableKeyException(e.getMessage());
        }catch (Exception e){
            Log.e(TAG, "generateKey error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private CiphertextWrapper encryptData(String plaintext, Cipher cipher){
        try {
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new CiphertextWrapper(cipher.getIV(), ciphertext);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "encryptData error: " + e.getMessage());
        }
        return null;
    }

    private String decryptData(byte[] ciphertext, Cipher cipher){
        try {
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        }catch (AEADBadTagException e){
            Log.e(TAG, "decrypt verification failed: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "decryptData error: " + e.getMessage());
        }
        return "";
    }

    private CiphertextWrapper getCipherData(){
        byte[] iv = SPUtils.getBytes(BIOMETRIC_CIPHER_IV);
        byte[] data = SPUtils.getBytes(BIOMETRIC_CIPHER_DATA);
        if(iv == null || data == null){
            return null;
        }
        return new CiphertextWrapper(iv, data);
    }

    /**
     * 保存指纹加密数据，用于下次指纹解锁 解密数据
     */
    private void saveCipherData(CiphertextWrapper ciphertextWrapper){
        if(ciphertextWrapper != null){
            SPUtils.setBytes(BIOMETRIC_CIPHER_IV, ciphertextWrapper.iv);
            SPUtils.setBytes(BIOMETRIC_CIPHER_DATA, ciphertextWrapper.ciphertext);
        }
    }

    public static class CiphertextWrapper{
        public byte[] ciphertext;
        public byte[] iv;

        public CiphertextWrapper(byte[] ciphertext, byte[] iv) {
            this.ciphertext = ciphertext;
            this.iv = iv;
        }

        public byte[] getCiphertext() {
            return ciphertext;
        }

        public void setCiphertext(byte[] ciphertext) {
            this.ciphertext = ciphertext;
        }

        public byte[] getIv() {
            return iv;
        }

        public void setIv(byte[] iv) {
            this.iv = iv;
        }
    }

    //    private Cipher getCipher(String keyName){
//        try {
//            String transformation = KEY_ALGORITHM_AES + "/" + BLOCK_MODE_CBC + "/" + ENCRYPTION_PADDING_PKCS7;
//            Cipher cipher = Cipher.getInstance(transformation);
//
//            SecretKey secretKey = getSecretKey(keyName);
//            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
//
//            return cipher;
//        }catch (Exception e){
//            Log.e(TAG, "init Cipher failed: " + e.getMessage());
//        }
//        return null;
//    }
//
//    private SecretKey getSecretKey(String keyName) {
//        try {
//            KeyGenerator keyGenerator = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM, ANDROID_KEYSTORE);
//            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
//
//            keyStore.load(null);
//            keyStore.getKey(keyName, null);
//
//            int keyProperties = KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT;
//            boolean invalidatedByBiometricEnrollment = true;
//            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(keyName, keyProperties)
//                    .setBlockModes(BLOCK_MODE_CBC)
//                    .setUserAuthenticationRequired(true)
//                    .setEncryptionPaddings(ENCRYPTION_PADDING_PKCS7);
//            //.setInvalidatedByBiometricEnrollment(invalidatedByBiometricEnrollment);
//            keyGenerator.init(builder.build());
//            return keyGenerator.generateKey();
//        }catch (Exception e){
//            e.printStackTrace();
//            Log.e(TAG, "init SecretKey failed: " + e.getMessage());
//        }
//        return null;
//    }
}
