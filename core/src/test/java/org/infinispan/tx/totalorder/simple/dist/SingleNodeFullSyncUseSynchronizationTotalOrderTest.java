package org.infinispan.tx.totalorder.simple.dist;

import org.testng.annotations.Test;

/**
 * @author Pedro Ruivo
 * @since 5.3
 */
@Test(groups = "functional", testName = "tx.totalorder.simple.dist.SingleNodeFullSyncUseSynchronizationTotalOrderTest")
public class SingleNodeFullSyncUseSynchronizationTotalOrderTest extends FullSyncUseSynchronizationTotalOrderTest {

   public SingleNodeFullSyncUseSynchronizationTotalOrderTest() {
      super(1);
   }
}
