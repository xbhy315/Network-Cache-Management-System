package com.cache.client.verify;

import com.cache.client.net.MockCacheClient;
import com.cache.client.net.protocol.RespCodec;
import com.cache.client.net.protocol.RespResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 审查修改验证 Demo。
 *
 * 验证三个修改的正确性：
 *   修改1 — RespCodecTest: LPUSH 编码 *4 → *5
 *   修改2 — RespCodecTest: getError() 预期值补全 ERR 前缀
 *   修改3 — MockCacheClient: lrange() subList 视图 → 独立副本
 *
 * 运行方式：
 *   在 IDEA 中右键此类 → Run 'FixVerificationDemo.main()'
 *   或终端：mvn exec:java（如果配置了 exec-maven-plugin）
 *
 * 判断标准：
 *   控制台全部输出 [PASS]，没有任何 [FAIL] → 三个修改均正确
 *   出现 [FAIL] → 对应修改有问题，需复查
 */
public class FixVerificationDemo {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("审查修改验证 Demo");
        System.out.println("=".repeat(60));

        verifyFix1_lpushEncode();
        verifyFix2_decodeError();
        verifyFix3_subListCopy();

        System.out.println("\n" + "=".repeat(60));
        System.out.printf("结果: %d PASS, %d FAIL%n", pass, fail);
        if (fail == 0) {
            System.out.println("结论: 三个修改全部正确 ✅");
        } else {
            System.out.println("结论: 存在失败项，请复查 ❌");
        }
        System.out.println("=".repeat(60));
    }

    // ================================================================
    // 修改1：LPUSH 编码 *4 → *5
    // ================================================================

    static void verifyFix1_lpushEncode() {
        System.out.println("\n--- 修改1: LPUSH 编码数组头计数 ---");

        // arrange: 编码 LPUSH 命令
        byte[] encoded = RespCodec.encode("LPUSH", "q", "a", "b", "c");
        String result = new String(encoded, StandardCharsets.UTF_8);

        // 解释：参数个数 = 命令(1) + key(1) + values(3) = 5
        //       正确的 RESP 数组头应为 *5
        boolean startsWithStar5 = result.startsWith("*5\r\n");
        boolean startsWithStar4 = result.startsWith("*4\r\n");

        System.out.println("  编码结果第一行: " + result.substring(0, result.indexOf("\r\n") + 2).trim());

        check("数组头应为 *5（5个参数）", startsWithStar5);
        check("数组头不应为 *4（旧错误预期）", !startsWithStar4);

        // 补充验证：空参编码
        byte[] pingEncoded = RespCodec.encode("PING");
        String pingResult = new String(pingEncoded, StandardCharsets.UTF_8);
        check("PING 命令 → *1（1个参数验证基准）", pingResult.startsWith("*1\r\n"));
    }

    // ================================================================
    // 修改2：getError() 预期值补全 ERR 前缀
    // ================================================================

    static void verifyFix2_decodeError() {
        System.out.println("\n--- 修改2: 错误解码 ERR 前缀 ---");

        // arrange: 构造 RESP 错误响应并解码
        String resp = "-ERR unknown command\r\n";
        RespResponse result;
        try {
            InputStream in = new ByteArrayInputStream(resp.getBytes(StandardCharsets.UTF_8));
            result = RespCodec.decode(in);
        } catch (Exception e) {
            check("解码不抛异常", false);
            e.printStackTrace();
            return;
        }

        // 关键验证
        String errorMsg = result.getError();

        System.out.println("  getError() 实际值: \"" + errorMsg + "\"");

        check("getError() 包含 ERR 前缀（完整消息）",
                "ERR unknown command".equals(errorMsg));

        check("getError() 不是只有描述部分（旧错误预期）",
                !"unknown command".equals(errorMsg));

        check("isError() 返回 true", result.isError());

        // 解释：Redis 标准中，错误前缀（ERR/WRONGTYPE/NOSUCHKEY）是消息的一部分
        //       保留完整前缀让调用方可以区分错误类型
    }

    // ================================================================
    // 修改3：lrange() subList 视图 → 独立副本
    // ================================================================

    static void verifyFix3_subListCopy() {
        System.out.println("\n--- 修改3: lrange() subList 视图 → 独立副本 ---");

        MockCacheClient client = new MockCacheClient();
        client.connect("localhost", 6379);

        // arrange: 创建一个列表
        client.rpush("testlist", "a", "b", "c", "d", "e");
        System.out.println("  初始列表: [a, b, c, d, e]");

        // act: 获取 lrange 结果
        List<String> range1 = client.lrange("testlist", 0, -1);
        System.out.println("  lrange(0,-1) 返回类型: " + range1.getClass().getSimpleName());

        // 关键验证1：返回的应该是独立 ArrayList，不是 COWSubList
        boolean isArrayList = range1.getClass().equals(java.util.ArrayList.class);
        check("返回值是 ArrayList（独立副本）而非 COWSubList（视图）", isArrayList);

        // act: 修改原列表
        client.lpop("testlist");  // 弹出 a
        System.out.println("  lpop 后原列表: [b, c, d, e]");

        // 关键验证2：之前的 lrange 结果不受 lpop 影响
        try {
            boolean range1Unchanged = range1.equals(List.of("a", "b", "c", "d", "e"));
            check("lpop 后之前 lrange 结果不受影响", range1Unchanged);
        } catch (Exception e) {
            check("lpop 后访问之前 lrange 结果不抛异常", false);
            System.out.println("  异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        // 关键验证3：新 lrange 反映最新状态
        List<String> range2 = client.lrange("testlist", 0, -1);
        boolean range2Updated = range2.equals(List.of("b", "c", "d", "e"));
        check("新的 lrange 反映最新列表状态", range2Updated);

        // 边界：子范围的 subList → 独立副本
        List<String> range3 = client.lrange("testlist", 1, 2);  // [c, d]
        client.lpop("testlist");  // 弹出 b
        try {
            boolean range3Unchanged = range3.equals(List.of("c", "d"));
            check("子范围 lrange 结果也不受 lpop 影响", range3Unchanged);
        } catch (Exception e) {
            check("lpop 后访问子范围 lrange 结果不抛异常", false);
        }
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    static void check(String description, boolean condition) {
        if (condition) {
            pass++;
            System.out.println("  [PASS] " + description);
        } else {
            fail++;
            System.out.println("  [FAIL] " + description);
        }
    }
}
