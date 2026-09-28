package cn.bugstack.domain.demo.service;

import cn.bugstack.domain.demo.model.valobj.DemoResetResultVO;

/**
 * Demo-only data restoration boundary. Implementations are registered only for the demo profile.
 */
public interface IDemoResetService {

    String DEMO_USER_ID = "demo_user";

    DemoResetResultVO reset(String authenticatedUserId);

}
