package com.volcengine.vertcdemo;

import static com.volcengine.vertcdemo.core.net.rtm.RtmInfo.KEY_RTM;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import com.ss.video.rtc.demo.basic_module.utils.SafeToast;
import com.ss.video.rtc.demo.basic_module.utils.Utilities;
import com.volcengine.vertcdemo.core.SolutionDataManager;
import com.volcengine.vertcdemo.core.net.IRequestCallback;
import com.volcengine.vertcdemo.core.net.ServerResponse;
import com.volcengine.vertcdemo.core.net.http.HttpRequestHelper;
import com.volcengine.vertcdemo.core.net.rtm.RtmInfo;
import com.volcengine.vertcdemo.feedshare.feature.FeedShareEntryActivity;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

public class SceneEntryFragment extends Fragment {
    public static final String TAG = "SceneEntryFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scene_entry, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setPackage(BuildConfig.APPLICATION_ID);
        intent.addCategory(Actions.CATEGORY_SCENE);

        Context context = requireContext();
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> scenes = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA);
        LinearLayout cards = view.findViewById(R.id.cards);
        LayoutInflater inflater = LayoutInflater.from(context);

        Collections.sort(scenes, (o1, o2) -> readSceneSort(o1) - readSceneSort(o2));

        for (ResolveInfo scene : scenes) {
            View card = inflater.inflate(R.layout.item_scene_entry, cards, false);
            ImageView icon = card.findViewById(R.id.icon);
            TextView label = card.findViewById(R.id.text);


            final int iconRes = scene.getIconResource();
            if (iconRes != ResourcesCompat.ID_NULL) {
                icon.setImageResource(iconRes);
            } else {
                icon.setImageDrawable(scene.loadIcon(packageManager));
            }

            label.setText(scene.loadLabel(packageManager));
            card.setOnClickListener(createSceneHandler(scene));
            cards.addView(card);
        }
    }

    private View.OnClickListener createSceneHandler(ResolveInfo scene) {
        return v -> {
            if (scene == null || scene.activityInfo == null || TextUtils.isEmpty(scene.activityInfo.name)) {
                SafeToast.show("Enter scene failed by activityInfo is empty!");
                return;
            }
            String sceneNameAbbr = extractSceneNameAbbr(scene);
            if (TextUtils.isEmpty(sceneNameAbbr)) {
                SafeToast.show("SceneCode not set");
                return;
            }
            String token = SolutionDataManager.ins().getToken();
            if (TextUtils.isEmpty(token)) {
                SafeToast.show("Token not found.");
                return;
            }
            startScene(sceneNameAbbr, token, scene.activityInfo.name);
        };
    }

    /***
     * ??????????????????
     * @param sceneNameAbbr ???????????????
     * @param token ??????token
     * @param targetActivity ?????????????????????????????????Activity??????
     */
    private void startScene(String sceneNameAbbr, String token, String targetActivity) {
        setAppInfoAndJoinRTM(sceneNameAbbr, targetActivity, token, new IRequestCallback<ServerResponse<RtmInfo>>() {
            @Override
            public void onSuccess(ServerResponse<RtmInfo> response) {
                if (!isVisible()) {
                    return;
                }
                RtmInfo data = response == null ? null : response.getData();
                if (data == null || !data.isValid()) {
                    onError(-1, "");
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Actions.CATEGORY_SCENE);
                intent.setClassName(Utilities.getApplicationContext().getPackageName(), targetActivity);
                intent.putExtra(KEY_RTM, data);
                startActivity(intent);
            }

            @Override
            public void onError(int errorCode, String message) {
                SafeToast.show(getString(R.string.request_rtm_fail));
            }
        });
    }

    @Nullable
    private static String extractSceneNameAbbr(ResolveInfo scene) {
        Bundle metaData = scene.activityInfo.metaData;
        return metaData == null ? null : metaData.getString("scene_name_abbr");
    }

    /**
     * ?????????????????????RTM???????????????????????????????????????
     *
     * @param scenesNameAbbr ???????????????
     * @param loginToken     ??????Token
     * @param callBack       ????????????
     */
    private static void setAppInfoAndJoinRTM(String scenesNameAbbr,
                                             String targetActivity,
                                             String loginToken,
                                             IRequestCallback<ServerResponse<RtmInfo>> callBack) {
        try {
            JSONObject content = new JSONObject();
            content.put("app_id", VolcConstants.APP_ID);
            content.put("app_key", VolcConstants.APP_KEY);
            content.put("volc_ak", VolcConstants.VOLC_AK);
            content.put("volc_sk", VolcConstants.VOLC_SK);
            content.put("scenes_name", scenesNameAbbr);
            content.put("login_token", loginToken);

            content.put("account_id", VolcConstants.ACCOUNT_ID);
            content.put("vod_space", VolcConstants.VOD_SPACE);

            //content.put("content_partner", VolcConstants.FEED_SHARE_CONTENT_PARTNER);
            //content.put("content_category", VolcConstants.FEED_SHARE_CONTENT_CATEGORY);

            JSONObject params = new JSONObject();
            params.put("event_name", "setAppInfo");
            params.put("content", content.toString());
            params.put("device_id", SolutionDataManager.ins().getDeviceId());

            HttpRequestHelper.sendPost(params, RtmInfo.class, callBack);
        } catch (Exception e) {
            Log.d(TAG, "setAppInfo failed", e);
            callBack.onError(-1, e.getMessage());
        }
    }

    /**
     * ???????????? ResolveInfo ???????????? scene_sort ??????
     *
     * @return sort ??????
     */
    static int readSceneSort(ResolveInfo info) {
        if (info.activityInfo == null) {
            return 10000;
        }

        if (info.activityInfo.metaData == null) {
            return 10000;
        }

        return info.activityInfo.metaData.getInt("scene_sort", 10000);
    }
}