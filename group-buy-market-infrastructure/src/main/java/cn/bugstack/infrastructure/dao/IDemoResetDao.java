package cn.bugstack.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MySQL operations used only by the demo-profile reset service. */
@Mapper
public interface IDemoResetDao {

    List<Long> lockProcessingRefundRequestIds(@Param("userId") String userId);

    int deleteRefundRequests(@Param("userId") String userId);

    int deleteDemoNotifyTasks();

    int restoreDemoTeams();

    int restoreDemoOrders();

}
