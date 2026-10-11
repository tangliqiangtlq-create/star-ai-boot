package org.jeecg.config.aspect;

import org.jeecg.common.exception.E5RejectException;
import org.junit.Assert;
import org.junit.Test;

public class E5TemplateApprovalTest {

    // Local POJO mirror of SysMessageTemplate (to avoid circular jeecg-system-biz dep in jeecg-boot-base-core)
    public static class FakeTemplate {
        private String templateCode;
        private String status;
        public String getTemplateCode() { return templateCode; }
        public void setTemplateCode(String v) { this.templateCode = v; }
        public String getStatus() { return status; }
        public void setStatus(String v) { this.status = v; }
    }

    @Test
    public void testDraftStatusRejectedByGuard() {
        FakeTemplate tpl = new FakeTemplate();
        tpl.setTemplateCode("E5-TEST-001");
        tpl.setStatus("DRAFT");
        try {
            E5TemplateRenderGuardAspect.checkTemplateContentStatus(tpl);
            Assert.fail("Expected E5RejectException not thrown for DRAFT status");
        } catch (E5RejectException ex) {
            Assert.assertTrue(ex.getMessage().contains("not APPROVED"));
        }
    }

    @Test
    public void testApprovedStatusPassesGuard() {
        FakeTemplate tpl = new FakeTemplate();
        tpl.setTemplateCode("E5-TEST-002");
        tpl.setStatus("APPROVED");
        try {
            E5TemplateRenderGuardAspect.checkTemplateContentStatus(tpl);
        } catch (E5RejectException ex) {
            Assert.fail("APPROVED status must not throw, got=" + ex.getMessage());
        }
    }

    @Test
    public void testNullTemplateDoesNotThrow() {
        try {
            E5TemplateRenderGuardAspect.checkTemplateContentStatus(null);
        } catch (Throwable t) {
            Assert.fail("null template should not throw");
        }
    }
}
