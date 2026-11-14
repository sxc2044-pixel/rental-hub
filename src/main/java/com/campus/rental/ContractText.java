package com.campus.rental;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** 居住租赁条款快照；参考GF—2025—2614的事项结构，非官方示范文本原件。 */
public final class ContractText {
  private ContractText() {}

  public static String template() {
    return create(Map.of("title", "【房源名称】", "address", "【详细地址】", "area", "【面积】",
            "layout", "【户型】", "facilities", "【交接设施清单】", "rent", "【月租金额】"),
        Map.of("start_date", "【开始日期】", "end_date", "【结束日期】"),
        Map.of("name", "【出租人姓名】", "id", "【出租人账号】", "phone", "【联系电话】"),
        Map.of("name", "【承租人姓名】", "id", "【承租人账号】", "phone", "【联系电话】"),
        "【双方约定金额】", "【双方确认的补充事项；无则填写无】");
  }

  public static String hash(Object terms) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(terms.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String create(Map<String,Object> h, Map<String,Object> a,
      Map<String,Object> landlord, Map<String,Object> tenant, String deposit, String extra) {
    return """
        城镇房屋租赁合同
        平台条款版本 RH-2026-11；本合同为双方居住租赁约定，参考GF—2025—2614事项结构编写。

        第一条 当事人与出租权
        出租人（甲方）：%s，平台账号ID：%s，联系电话：%s。
        承租人（乙方）：%s，平台账号ID：%s，联系电话：%s。
        上述为账号资料快照，不代表平台已核验真实身份。甲方应出示有效身份证明、产权或合法出租授权，乙方应核对权属、用途及安全条件。双方应在签署前自行核验。

        第二条 房屋与用途
        房源：%s；地址：%s；建筑面积：%s平方米；户型：%s。
        配套设施：%s。房屋仅供合法居住，不得用于违法活动或擅自改变用途。

        第三条 租期与交付
        租期自%s起至%s止。甲方按约交付适合居住的房屋，双方交接时另行记录家具设备、钥匙数量、水电气表底数及现存瑕疵，并各自保存交接清单。

        第四条 租金与支付
        月租人民币%s元，按月支付；首期于交付当日支付，后续每期于对应租赁月首日支付（当月无对应日时取月末日）。双方线下确认收款账户并留存凭据；平台开单及结清记录不代表资金实际划转。租期内未经双方协商不得单方提高租金。

        第五条 押金与费用
        押金人民币%s元。押金不替代租金；退租交接并结清应承担费用后7日内退还余额，扣除部分应提供项目、依据和凭证，不得无故扣留。水、电、燃气按实际用量及合法计费标准由乙方承担，物业费由甲方承担；其他费用须双方另行书面约定，不得单方收费。

        第六条 维修与安全
        甲方承担房屋及附属设施正常损耗的维修义务；乙方合理使用并及时报告故障。因乙方过错造成损坏的，由乙方依法承担相应责任。紧急危及人身财产安全时应先采取必要避险措施并及时通知。维修费用、责任和处理结果据实记录，责任有争议时协商或依法解决。

        第七条 使用与隐私
        未经甲方书面同意不得转租、拆改主体结构。甲方检查或维修应提前沟通，经乙方同意后进入，紧急情况依法处理；双方妥善保护对方个人信息，不得向无关人员披露。

        第八条 解除与返还
        提前退租、续租或变更应及时协商并形成书面记录。依法享有的解除权不因平台审批流程受限。到期或依法解除后，双方完成房屋、钥匙、表底与费用交接；平台退租审批用于业务登记，不替代法定权利。违约责任以实际约定及法律规定为准，不设无依据的罚款。

        第九条 争议处理
        争议先协商，可依法申请调解；协商不成可向有管辖权的人民法院起诉。未尽事项由双方另行书面约定，补充协议与交接清单应共同保存。

        第十条 电子确认与保存
        双方自愿选择平台电子确认，分别阅读完整正文、核对姓名并复核登录密码。双方确认后平台状态记为已签署，各方可打印或保存PDF。系统保存正文SHA-256摘要、账号、签署姓名及服务器时间。本平台未提供实名认证、CA数字证书、可信时间戳或第三方存证，不承诺达到可靠电子签名标准；法律效力应结合身份、真实意思及证据依法判断，真实交易可另行选择纸质签约或依法提供认证服务的机构。

        第十一条 补充约定
        %s
        """.formatted(landlord.get("name"), landlord.get("id"), landlord.get("phone"),
            tenant.get("name"), tenant.get("id"), tenant.get("phone"), h.get("title"),
            h.get("address"), h.get("area"), h.get("layout"), h.get("facilities"),
            a.get("start_date"), a.get("end_date"), h.get("rent"), deposit, extra);
  }
}
