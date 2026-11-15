package es.academia.campushub.support;

import org.springframework.core.Ordered;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public class ContextoSucioTrasClaseListener extends AbstractTestExecutionListener {

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public void afterTestClass(TestContext testContext) {
    testContext.markApplicationContextDirty(HierarchyMode.EXHAUSTIVE);
  }
}
