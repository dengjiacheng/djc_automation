package com.automation.application.runtime.modules;

import com.automation.infrastructure.vision.ImageRecognition;
import com.automation.domain.command.CommandContext;
import com.automation.domain.command.CommandModule;
import com.automation.domain.command.CommandParameter;
import com.automation.domain.command.CommandRegistry;
import com.automation.domain.command.CommandResult;
import com.automation.domain.scenario.vision.VisionToolkit;

import org.json.JSONObject;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.util.Arrays;
import java.util.List;

/**
 * 图像相关指令：模板匹配、图片比对。
 */
public final class VisionCommandModule implements CommandModule {

    private static final List<CommandParameter> FIND_TEMPLATE_PARAMS = Arrays.asList(
            CommandParameter.optional("threshold", "float", "匹配阈值(0-1)", 0.8f),
            CommandParameter.optional("screenshot", "string", "截图文件路径", ""),
            CommandParameter.optional("screenshot_base64", "string", "截图Base64 数据", ""),
            CommandParameter.optional("template", "string", "模板文件路径", ""),
            CommandParameter.optional("template_base64", "string", "模板Base64 数据", "")
    );

    private static final List<CommandParameter> COMPARE_PARAMS = Arrays.asList(
            CommandParameter.required("image1", "string", "要比较的第一张图片路径", ""),
            CommandParameter.required("image2", "string", "要比较的第二张图片路径", "")
    );

    private final ImageRecognition imageRecognition;
    private final VisionToolkit visionToolkit;

    public VisionCommandModule(ImageRecognition imageRecognition, VisionToolkit visionToolkit) {
        this.imageRecognition = imageRecognition;
        this.visionToolkit = visionToolkit;
    }

    @Override
    public void register(CommandRegistry registry) {
        registry.register("find_template", "模板匹配", FIND_TEMPLATE_PARAMS, this::findTemplate);
        registry.register("compare_images", "比较两张图片相似度", COMPARE_PARAMS, this::compareImages);
    }

    private CommandResult findTemplate(CommandContext context, JSONObject params) throws Exception {
        double threshold = params.optDouble("threshold", 0.8);
        org.opencv.core.Point match;
        Mat screenshotMat = null;
        Mat templateMat = null;
        context.reportProgress("find_template", "开始模板匹配", null, null);
        try {
            screenshotMat = resolveMat(params, "screenshot_base64", "screenshot");
            templateMat = resolveMat(params, "template_base64", "template");
            if (screenshotMat == null || screenshotMat.empty() || templateMat == null || templateMat.empty()) {
                throw new IllegalArgumentException("无法获取截图或模板图像数据");
            }
            match = imageRecognition.findTemplate(screenshotMat, templateMat, threshold);
            JSONObject extra = new JSONObject();
            if (match != null) {
                extra.put("x", (int) match.x);
                extra.put("y", (int) match.y);
            }
            context.reportProgress("find_template", match != null ? "匹配成功" : "未匹配到模板",
                    match != null ? 100 : null, extra);
        } finally {
            if (screenshotMat != null) {
                screenshotMat.release();
            }
            if (templateMat != null) {
                templateMat.release();
            }
        }
        JSONObject response = new JSONObject();
        if (match != null) {
            response.put("found", true);
            response.put("x", (int) match.x);
            response.put("y", (int) match.y);
        } else {
            response.put("found", false);
        }
        return CommandResult.success(response);
    }

    private Mat resolveMat(JSONObject params, String base64Key, String pathKey) throws Exception {
        if (params.has(base64Key)) {
            return imageRecognition.decodeBase64(params.getString(base64Key));
        }
        String path = params.optString(pathKey, null);
        if (path != null) {
            return Imgcodecs.imread(path);
        }
        return null;
    }

    private CommandResult compareImages(CommandContext context, JSONObject params) throws Exception {
        String image1 = params.getString("image1");
        String image2 = params.getString("image2");
        double similarity = visionToolkit != null
                ? visionToolkit.compareImages(image1, image2)
                : imageRecognition.compareImages(image1, image2);
        JSONObject response = new JSONObject();
        response.put("similarity", similarity);
        response.put("is_similar", similarity >= 0.9);
        return CommandResult.success(response);
    }
}
