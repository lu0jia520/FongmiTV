package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.FragmentSettingPlayerBinding;
import com.fongmi.android.tv.impl.BufferCallback;
import com.fongmi.android.tv.impl.SubtitleCallback;
import com.fongmi.android.tv.impl.UaCallback;
import com.fongmi.android.tv.player.ExoUtil;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.dialog.BufferDialog;
import com.fongmi.android.tv.ui.custom.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.custom.dialog.UaDialog;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingPlayerFragment extends BaseFragment implements UaCallback, BufferCallback, SubtitleCallback {

    private FragmentSettingPlayerBinding mBinding;
    private String[] background;
    private String[] http;
    private String[] flag;

    public static SettingPlayerFragment newInstance() {
        return new SettingPlayerFragment();
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingPlayerBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mBinding.uaText.setText(Setting.getUa());
        mBinding.tunnelText.setText(getSwitch(Setting.isTunnel()));
        mBinding.bufferText.setText(String.valueOf(Setting.getBuffer()));
        mBinding.subtitleText.setText(String.valueOf(Setting.getSubtitle()));
        mBinding.flagText.setText((flag = ResUtil.getStringArray(R.array.select_flag))[Setting.getFlag()]);
        mBinding.httpText.setText((http = ResUtil.getStringArray(R.array.select_exo_http))[Setting.getHttp()]);
        mBinding.backgroundText.setText((background = ResUtil.getStringArray(R.array.select_background))[Setting.getBackground()]);
        setVisible();
    }

    @Override
    protected void initEvent() {
        mBinding.ua.setOnClickListener(this::onUa);
        mBinding.http.setOnClickListener(this::setHttp);
        mBinding.flag.setOnClickListener(this::setFlag);
        mBinding.buffer.setOnClickListener(this::onBuffer);
        mBinding.tunnel.setOnClickListener(this::setTunnel);
        mBinding.subtitle.setOnClickListener(this::onSubtitle);
        mBinding.background.setOnClickListener(this::setBackground);
    }

    private void setVisible() {
        mBinding.http.setVisibility(Players.isExo(Setting.getPlayer()) ? View.VISIBLE : View.GONE);
        mBinding.buffer.setVisibility(Players.isExo(Setting.getPlayer()) ? View.VISIBLE : View.GONE);
        mBinding.tunnel.setVisibility(Players.isExo(Setting.getPlayer()) ? View.VISIBLE : View.GONE);
    }

    private void onUa(View view) {
        UaDialog.create(this).show();
    }

    private void setHttp(View view) {
        int index = Setting.getHttp();
        Setting.putHttp(index = index == http.length - 1 ? 0 : ++index);
        mBinding.httpText.setText(http[index]);
        ExoUtil.reset();
    }

    private void setFlag(View view) {
        int index = Setting.getFlag();
        Setting.putFlag(index = index == flag.length - 1 ? 0 : ++index);
        mBinding.flagText.setText(flag[index]);
    }

    private void setTunnel(View view) {
        Setting.putTunnel(!Setting.isTunnel());
        mBinding.tunnelText.setText(getSwitch(Setting.isTunnel()));
    }

    private void onBuffer(View view) {
        BufferDialog.create(this).show();
    }

    private void onSubtitle(View view) {
        SubtitleDialog.create(this).show();
    }

    private void setBackground(View view) {
        new MaterialAlertDialogBuilder(getActivity()).setTitle(R.string.setting_player_background).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(background, Setting.getBackground(), (dialog, which) -> {
            mBinding.backgroundText.setText(background[which]);
            Setting.putBackground(which);
            dialog.dismiss();
        }).show();
    }

    @Override
    public void setUa(String ua) {
        mBinding.uaText.setText(ua);
        Setting.putUa(ua);
    }

    @Override
    public void setBuffer(int times) {
        mBinding.bufferText.setText(String.valueOf(times));
        Setting.putBuffer(times);
    }

    @Override
    public void setSubtitle(int size) {
        mBinding.subtitleText.setText(String.valueOf(size));
        Setting.putSubtitle(size);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) setVisible();
    }
}
