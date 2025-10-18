package com.automation.feature.scripts.dhgate;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import com.automation.infrastructure.system.AppManager;
import com.automation.domain.scenario.SceneHandler;
import com.automation.domain.scenario.SceneResult;
import com.automation.domain.scenario.ScenarioContext;
import com.automation.domain.scenario.accessibility.AccessibilitySnapshot;
import com.automation.domain.scenario.device.DeviceActions;
import com.automation.domain.scenario.device.SwipeDirection;
import com.automation.domain.scenario.script.ScriptHandlerProvider;
import com.automation.domain.scenario.vision.VisionToolkit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DHgate 下单脚本的所有场景处理器。
 */
public final class DhgateOrderV2Handlers implements ScriptHandlerProvider {

    private static final String TAG = "DhgateOrderHandlers";
    private static final String SCRIPT_NAME = "dhgate_order_v2";
    private static final String PACKAGE_NAME = "com.dhgate.buyermob";

    @Override
    public boolean supports(String scriptName) {
        return SCRIPT_NAME.equalsIgnoreCase(scriptName);
    }

    @Override
    public SceneHandler resolve(String handlerName) {
        return switch (handlerName) {
            case "handle_start_app" -> DhgateOrderV2Handlers::handleStartApp;
            case "handle_home_page_enter_search" -> DhgateOrderV2Handlers::handleHomeEnterSearch;
            case "handle_search_page" -> DhgateOrderV2Handlers::handleSearchPage;
            case "handle_search_result_page" -> DhgateOrderV2Handlers::handleSearchResultPage;
            case "handle_product_detail_page" -> DhgateOrderV2Handlers::handleProductDetailPage;
            case "handle_product_detail_page_a" -> DhgateOrderV2Handlers::handleProductDetailPageAlternative;
            case "handle_sku_selection_page" -> DhgateOrderV2Handlers::handleSkuSelectionAddToCart;
            case "handle_sku_selection_page_a" -> DhgateOrderV2Handlers::handleSkuSelectionBuyNow;
            case "handle_product_recommend_page" -> DhgateOrderV2Handlers::handleProductRecommendPage;
            case "handle_cart_checkout_page" -> DhgateOrderV2Handlers::handleCartCheckoutPage;
            case "handle_order_confirm_page" -> DhgateOrderV2Handlers::handleOrderConfirmPage;
            case "handle_secure_payment_page" -> DhgateOrderV2Handlers::handleSecurePaymentPage;
            case "handle_card_info_page" -> DhgateOrderV2Handlers::handleCardInfoPage;
            case "handle_payment_exception_page" -> DhgateOrderV2Handlers::handlePaymentExceptionPage;
            case "handle_payment_failed_page" -> DhgateOrderV2Handlers::handlePaymentFailedPage;
            case "handle_no_payment_method_dialog" -> DhgateOrderV2Handlers::handleNoPaymentMethodDialog;
            case "handle_upgrade_dialog" -> ctx -> clickAndContinue(ctx, By.res(PACKAGE_NAME, "btn_cancel"));
            case "handle_coupon_dialog" -> ctx -> clickAndContinue(ctx, By.res(PACKAGE_NAME, "close"));
            case "handle_congrats_dialog" -> ctx -> clickAndContinue(ctx, By.res(PACKAGE_NAME, "close"));
            case "handle_category_dialog" -> ctx -> clickAndContinue(ctx, By.res(PACKAGE_NAME, "btn_close"));
            case "handle_rate_app_dialog" -> ctx -> clickAndContinue(ctx, By.res(PACKAGE_NAME, "btn_ok"));
            case "handle_deal_dialog" -> ctx -> clickAndContinue(ctx, By.res(PACKAGE_NAME, "auto_close"));
            case "handle_product_not_found_page" -> DhgateOrderV2Handlers::handleProductNotFound;
            default -> null;
        };
    }

    private static SceneResult handleStartApp(ScenarioContext context) throws Exception {
        Context appContext = context.getAppContext();
        UiDevice device = context.getUiDevice();
        AppManager appManager = new AppManager(appContext, device);
        Log.i(TAG, "停止并启动应用: " + PACKAGE_NAME);
        appManager.stopApp(PACKAGE_NAME);
        Thread.sleep(4000);
        if (!appManager.launchApp(PACKAGE_NAME)) {
            Log.e(TAG, "应用启动失败");
            return SceneResult.ERROR;
        }
        Thread.sleep(3000);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handleHomeEnterSearch(ScenarioContext context) throws Exception {
        UiDevice device = context.getUiDevice();
        UiObject2 searchContainer = device.findObject(By.res(PACKAGE_NAME, "bar_search_view"));
        if (searchContainer == null) {
            Log.w(TAG, "首页搜索容器不存在");
            return SceneResult.ERROR;
        }
        UiObject2 textView = searchContainer.findObject(By.clazz("android.widget.TextView"));
        if (textView != null) {
            textView.click();
        } else {
            searchContainer.click();
        }
        Thread.sleep(1000);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handleSearchPage(ScenarioContext context) throws Exception {
        UiDevice device = context.getUiDevice();
        String keyword = optString(context, "search_keyword");
        if (keyword == null || keyword.isEmpty()) {
            Log.e(TAG, "缺少搜索关键词");
            return SceneResult.ERROR;
        }
        UiObject2 input = device.findObject(By.res(PACKAGE_NAME, "autoTV_search_auto_text"));
        if (input == null) {
            Log.w(TAG, "搜索输入框不存在");
            return SceneResult.ERROR;
        }
        input.setText(keyword);
        Thread.sleep(300);
        UiObject2 searchBtn = device.findObject(By.res(PACKAGE_NAME, "iv_search"));
        if (searchBtn != null) {
            searchBtn.click();
        }
        Thread.sleep(2000);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handleSearchResultPage(ScenarioContext context) throws Exception {
        UiDevice device = context.getUiDevice();
        ScenarioProgress progress = ScenarioProgress.from(context);
        String targetTitle = optString(context, "target_product_title");

        ensureVerticalProductLayout(device);

        List<ProductItem> products = collectProducts(device, context.getSnapshot());
        List<ProductItem> matched = TextUtils.isEmpty(targetTitle)
                ? List.of()
                : findProductsByTitle(products, targetTitle);

        Point templatePoint = null;
        String templateId = ensureProductTemplateLoaded(context);
        if (templateId != null && !templateId.isEmpty()) {
            VisionToolkit toolkit = context.getVisionToolkit();
            double threshold = parseDouble(context, "product_image_threshold", 0.82d);
            try {
                templatePoint = toolkit.findTemplateFromCache(templateId, threshold);
            } catch (IOException e) {
                Log.w(TAG, "模板匹配失败", e);
            }
        }

        if (!matched.isEmpty()) {
            ProductItem firstMatch = matched.get(0);
            ProductItem matchedByTemplate = templatePoint != null
                    ? matchPointInProducts(matched, templatePoint)
                    : null;

            Point clickPoint = matchedByTemplate != null
                    ? templatePoint
                    : firstMatch.center();

            Log.i(TAG, "命中目标商品: " + firstMatch.title()
                    + (matchedByTemplate != null ? "（通过模板定位）" : ""));
            device.click(clickPoint.x, clickPoint.y);
            Thread.sleep(2000);
            return SceneResult.CONTINUE;
        }

        if (templatePoint != null) {
            ProductItem candidate = matchPointInProducts(products, templatePoint);
            if (candidate != null) {
                Log.i(TAG, "通过模板定位商品: " + candidate.title());
                device.click(templatePoint.x, templatePoint.y);
                Thread.sleep(2000);
                return SceneResult.CONTINUE;
            }
        }

        // 未命中时执行随机浏览逻辑
        if (progress.canBrowseRandomly() && progress.shouldBrowseRandomly()) {
            progress.incrementRandomBrowse();
            UiObject2 listItem = pickRandomProduct(device);
            if (listItem != null) {
                Log.i(TAG, "随机浏览商品");
                listItem.click();
                Thread.sleep(1500);
                return SceneResult.CONTINUE;
            }
        }

        if (!progress.scrollProductList(context.getDeviceActions())) {
            return SceneResult.ERROR;
        }
        return SceneResult.CONTINUE;
    }

    private static SceneResult handleProductDetailPage(ScenarioContext context) throws Exception {
        return handleProductDetailCommon(context, false);
    }

    private static SceneResult handleProductDetailPageAlternative(ScenarioContext context) throws Exception {
        return handleProductDetailCommon(context, true);
    }

    private static SceneResult handleProductDetailCommon(ScenarioContext context, boolean buyNow) throws Exception {
        UiDevice device = context.getUiDevice();
        String targetShop = optString(context, "target_shop");
        if (TextUtils.isEmpty(targetShop)) {
            Log.e(TAG, "缺少目标店铺名称");
            return SceneResult.ERROR;
        }
        UiObject2 storeName = device.findObject(By.res(PACKAGE_NAME, "tv_store_name"));
        UiObject2 followBtn = device.findObject(By.res(PACKAGE_NAME, "tv_follow"));
        if (storeName != null && followBtn != null) {
            String actual = storeName.getText();
            if (targetShop.equalsIgnoreCase(actual)) {
                Log.i(TAG, "匹配店铺: " + actual);
                String followText = followBtn.getText();
                if ("_ Follow".equalsIgnoreCase(followText)) {
                    followBtn.click();
                    Thread.sleep(800);
                }
                if (buyNow) {
                    clickIfExists(device, By.res(PACKAGE_NAME, "btn_buy"));
                } else {
                    clickIfExists(device, By.res(PACKAGE_NAME, "btn_addtocart"));
                }
                Thread.sleep(1500);
                return SceneResult.CONTINUE;
            } else {
                Log.i(TAG, "店铺不匹配: " + actual);
                device.pressBack();
                Thread.sleep(800);
                return SceneResult.CONTINUE;
            }
        }

        context.getDeviceActions().swipe(SwipeDirection.UP, 0.6f, 600);
        Thread.sleep(600);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handleSkuSelectionAddToCart(ScenarioContext context) throws Exception {
        return handleSkuSelection(context, false);
    }

    private static SceneResult handleSkuSelectionBuyNow(ScenarioContext context) throws Exception {
        return handleSkuSelection(context, true);
    }

    private static SceneResult handleSkuSelection(ScenarioContext context, boolean buyNow) throws Exception {
        UiDevice device = context.getUiDevice();
        List<UiObject2> skuGroups = device.findObjects(By.res(PACKAGE_NAME, "sku_list"));
        if (skuGroups == null || skuGroups.isEmpty()) {
            Log.w(TAG, "未找到 SKU 选项");
            return SceneResult.CONTINUE;
        }
        Random random = ThreadLocalRandom.current();
        for (UiObject2 group : skuGroups) {
            List<UiObject2> options = group.findObjects(By.res(PACKAGE_NAME, "sku_root"));
            if (options.isEmpty()) {
                continue;
            }
            UiObject2 option = options.get(random.nextInt(options.size()));
            option.click();
            Thread.sleep(300);
        }

        incrementCounter(context, "add_to_cart_count");

        if (buyNow) {
            clickIfExists(device, By.res(PACKAGE_NAME, "two_buy_buy"));
        } else {
            clickIfExists(device, By.res(PACKAGE_NAME, "btn_paynow"));
        }
        Thread.sleep(1000);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handleProductRecommendPage(ScenarioContext context) throws Exception {
        UiDevice device = context.getUiDevice();
        int targetCount = parseInt(context, "target_cart_count", 2);
        int currentCount = getCounter(context, "add_to_cart_count");
        if (currentCount >= targetCount) {
            Log.i(TAG, "达到目标加购数量，进入购物车");
            clickIfExists(device, By.res(PACKAGE_NAME, "bar_pd_cart"));
        } else {
            Log.i(TAG, "继续加购，当前: " + currentCount);
            clickIfExists(device, By.res(PACKAGE_NAME, "btn_addtocart"));
        }
        Thread.sleep(1200);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handleCartCheckoutPage(ScenarioContext context) throws Exception {
        clickIfExists(context.getUiDevice(), By.res(PACKAGE_NAME, "btn_cart_checkout"));
        Thread.sleep(1200);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handleOrderConfirmPage(ScenarioContext context) throws Exception {
        clickIfExists(context.getUiDevice(), By.res(PACKAGE_NAME, "btn_confirm"));
        Thread.sleep(1200);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handleSecurePaymentPage(ScenarioContext context) throws Exception {
        clickIfExists(context.getUiDevice(), By.text("Add a new card"));
        Thread.sleep(1200);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handleCardInfoPage(ScenarioContext context) throws Exception {
        UiDevice device = context.getUiDevice();
        CardInfo card = CardInfo.random();
        fillChildInput(device, "sl_card_num", card.number());
        fillChildInput(device, "sl_card_data", card.expires());
        fillChildInput(device, "sl_card_cvv", card.cvv());
        Thread.sleep(500);
        clickIfExists(device, By.text("Pay Now"));
        Thread.sleep(1500);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handlePaymentExceptionPage(ScenarioContext context) throws Exception {
        clickIfExists(context.getUiDevice(), By.res(PACKAGE_NAME, "tv_refresh"));
        Thread.sleep(800);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handlePaymentFailedPage(ScenarioContext context) {
        Log.w(TAG, "支付失败，任务结束");
        return SceneResult.SUCCESS;
    }

    private static SceneResult handleNoPaymentMethodDialog(ScenarioContext context) throws Exception {
        clickIfExists(context.getUiDevice(), By.res(PACKAGE_NAME, "iv_close"));
        Thread.sleep(800);
        return SceneResult.CONTINUE;
    }

    private static SceneResult handleProductNotFound(ScenarioContext context) {
        context.getUiDevice().pressBack();
        return SceneResult.CONTINUE;
    }

    private static SceneResult clickAndContinue(ScenarioContext context, BySelector selector) throws Exception {
        clickIfExists(context.getUiDevice(), selector);
        Thread.sleep(600);
        return SceneResult.CONTINUE;
    }

    private static boolean clickIfExists(UiDevice device, BySelector selector) {
        if (device == null || selector == null) {
            return false;
        }
        UiObject2 obj = device.findObject(selector);
        if (obj == null) {
            return false;
        }
        obj.click();
        return true;
    }

    private static void fillChildInput(UiDevice device, String containerRes, String value) {
        UiObject2 container = device.findObject(By.res(PACKAGE_NAME, containerRes));
        if (container == null) {
            return;
        }
        UiObject2 input = container.findObject(By.res(PACKAGE_NAME, "edt_input_content"));
        if (input != null) {
            input.setText(value);
        }
    }

    @Nullable
    private static UiObject2 pickRandomProduct(UiDevice device) {
        List<UiObject2> items = device.findObjects(By.res(PACKAGE_NAME, "tv_name"));
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.get(ThreadLocalRandom.current().nextInt(items.size()));
    }

    private static void ensureVerticalProductLayout(UiDevice device) throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            if (isVerticalList(device)) {
                return;
            }
            if (!clickIfExists(device, By.res(PACKAGE_NAME, "iv_change_view"))) {
                break;
            }
            Thread.sleep(800);
        }
    }

    private static boolean isVerticalList(UiDevice device) {
        List<UiObject2> images = device.findObjects(By.res(PACKAGE_NAME, "item_top_image"));
        if (images == null || images.size() <= 1) {
            return true;
        }
        Rect firstBounds = images.get(0).getVisibleBounds();
        int reference = firstBounds.left;
        for (UiObject2 image : images) {
            Rect bounds = image.getVisibleBounds();
            if (Math.abs(bounds.left - reference) > 10) {
                return false;
            }
        }
        return true;
    }

    private static List<ProductItem> collectProducts(UiDevice device, AccessibilitySnapshot snapshot) {
        if (snapshot != null && !snapshot.isEmpty()) {
            List<ProductItem> fromSnapshot = collectFromSnapshot(snapshot);
            if (!fromSnapshot.isEmpty()) {
                return fromSnapshot;
            }
        }
        return collectFromUi(device);
    }

    private static List<ProductItem> collectFromSnapshot(AccessibilitySnapshot snapshot) {
        List<ProductItem> result = new ArrayList<>();
        for (AccessibilitySnapshot.Node node : snapshot.nodes()) {
            if (!PACKAGE_NAME.concat(":id/cl_pro_root").equals(node.resourceId())) {
                continue;
            }
            AccessibilitySnapshot.Node imageNode = findInSubtree(node, PACKAGE_NAME.concat(":id/item_top_image"));
            AccessibilitySnapshot.Node titleNode = findInSubtree(node, PACKAGE_NAME.concat(":id/tv_name"));
            Rect bounds = imageNode != null ? imageNode.bounds() : null;
            String title = titleNode != null ? titleNode.text() : null;
            if (bounds != null && !bounds.isEmpty()) {
                result.add(new ProductItem(title, bounds));
            }
        }
        return List.copyOf(result);
    }

    @Nullable
    private static AccessibilitySnapshot.Node findInSubtree(AccessibilitySnapshot.Node node, String targetResId) {
        if (node == null) {
            return null;
        }
        if (targetResId.equals(node.resourceId())) {
            return node;
        }
        for (AccessibilitySnapshot.Node child : node.children()) {
            AccessibilitySnapshot.Node found = findInSubtree(child, targetResId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<ProductItem> collectFromUi(UiDevice device) {
        List<UiObject2> roots = device.findObjects(By.res(PACKAGE_NAME, "cl_pro_root"));
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<ProductItem> result = new ArrayList<>(roots.size());
        for (UiObject2 root : roots) {
            UiObject2 imageNode = root.findObject(By.res(PACKAGE_NAME, "item_top_image"));
            if (imageNode == null) {
                continue;
            }
            Rect bounds = imageNode.getVisibleBounds();
            if (bounds == null || bounds.isEmpty()) {
                continue;
            }
            UiObject2 titleNode = root.findObject(By.res(PACKAGE_NAME, "tv_name"));
            String title = titleNode != null ? titleNode.getText() : null;
            result.add(new ProductItem(title, bounds));
        }
        return List.copyOf(result);
    }

    private static List<ProductItem> findProductsByTitle(List<ProductItem> products, String targetTitle) {
        if (products.isEmpty() || targetTitle == null) {
            return List.of();
        }
        List<ProductItem> result = new ArrayList<>();
        for (ProductItem item : products) {
            if (targetTitle.equalsIgnoreCase(item.title())) {
                result.add(item);
            }
        }
        return result;
    }

    @Nullable
    private static ProductItem matchPointInProducts(List<ProductItem> products, Point point) {
        if (products == null || products.isEmpty() || point == null) {
            return null;
        }
        for (ProductItem item : products) {
            if (item.contains(point)) {
                return item;
            }
        }
        return null;
    }

    @Nullable
    private static String optString(ScenarioContext context, String key) {
        Object value = context.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence chars) {
            return chars.toString();
        }
        if (value instanceof java.util.Map<?, ?> map) {
            Object nested = map.get("value");
            if (nested instanceof CharSequence nestedChars) {
                return nestedChars.toString();
            }
            if (nested != null) {
                return nested.toString();
            }
        }
        return value.toString();
    }

    private static int parseInt(ScenarioContext context, String key, int defaultValue) {
        Object value = context.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    @Nullable
    private static String ensureProductTemplateLoaded(ScenarioContext context) {
        String existing = optString(context, "product_image_template_id");
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        String base64 = optString(context, "product_image");
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        String taskName = optString(context, "task_name");
        String templateId = (taskName != null && !taskName.isEmpty())
                ? taskName + "_product_image"
                : "dhgate_product_image";
        try {
            context.getVisionToolkit().storeTemplate(templateId, base64);
            context.put("product_image_template_id", templateId);
            return templateId;
        } catch (IOException e) {
            Log.w(TAG, "加载 product_image 模板失败", e);
            return null;
        }
    }

    private static double parseDouble(ScenarioContext context, String key, double defaultValue) {
        Object value = context.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static void incrementCounter(ScenarioContext context, String key) {
        int current = getCounter(context, key);
        context.put(key, current + 1);
    }

    private static int getCounter(ScenarioContext context, String key) {
        Object value = context.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private record ProductItem(String title, Rect bounds) {
        ProductItem {
            bounds = bounds != null ? new Rect(bounds) : new Rect();
        }

        Point center() {
            return new Point((bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2);
        }

        boolean contains(Point point) {
            return point != null
                    && !bounds.isEmpty()
                    && point.x >= bounds.left
                    && point.x <= bounds.right
                    && point.y >= bounds.top
                    && point.y <= bounds.bottom;
        }
    }

    private record CardInfo(String number, String expires, String cvv) {
        static CardInfo random() {
            Random random = ThreadLocalRandom.current();
            StringBuilder number = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                number.append(random.nextInt(10));
            }
            int month = random.nextInt(1, 13);
            int year = random.nextInt(4) + 26;
            String expires = String.format(Locale.US, "%02d/%02d", month, year);
            String cvv = String.format(Locale.US, "%03d", random.nextInt(0, 1000));
            return new CardInfo(number.toString(), expires, cvv);
        }
    }

    private static final class ScenarioProgress {
        private final ScenarioContext context;
        private ScenarioProgress(ScenarioContext context) {
            this.context = context;
        }

        static ScenarioProgress from(ScenarioContext context) {
            return new ScenarioProgress(context);
        }

        boolean canBrowseRandomly() {
            int max = parseInt(context, "max_random_browse_count", 0);
            return getRandomBrowseCount() < max;
        }

        boolean shouldBrowseRandomly() {
            double probability = 0.0d;
            Object value = context.get("random_browse_probability");
            if (value instanceof Number number) {
                probability = number.doubleValue();
            } else if (value instanceof String str) {
                try {
                    probability = Double.parseDouble(str);
                } catch (NumberFormatException ignored) {
                }
            }
            double random = ThreadLocalRandom.current().nextDouble();
            Log.d(TAG, "随机浏览判定: random=" + random + ", probability=" + probability);
            return random < probability;
        }

        void incrementRandomBrowse() {
            int count = getRandomBrowseCount() + 1;
            context.put("_random_browse_count", count);
        }

        boolean scrollProductList(DeviceActions actions) throws InterruptedException {
            int count = getScrollCount();
            if (count >= 20000) {
                Log.e(TAG, "商品列表滑动次数达到上限: " + count);
                return false;
            }
            actions.swipe(SwipeDirection.UP, 0.8f, 600);
            context.put("_product_scroll_count", count + 1);
            Thread.sleep(800);
            return true;
        }

        private int getRandomBrowseCount() {
            Object value = context.get("_random_browse_count");
            if (value instanceof Number number) {
                return number.intValue();
            }
            return 0;
        }

        private int getScrollCount() {
            Object value = context.get("_product_scroll_count");
            if (value instanceof Number number) {
                return number.intValue();
            }
            return 0;
        }
    }
}
