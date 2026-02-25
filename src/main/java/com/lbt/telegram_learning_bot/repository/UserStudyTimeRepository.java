package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.UserStudyTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import static com.lbt.telegram_learning_bot.util.Constants.*;

@Repository
public interface UserStudyTimeRepository extends JpaRepository<UserStudyTime, Long> {
    Optional<UserStudyTime> findByUserIdAndTopicId(Long userId, Long topicId);
    List<UserStudyTime> findByUserIdAndTopicIdIn(Long userId, List<Long> topicIds);
    @Query("SELECT SUM(ust.totalSeconds) FROM UserStudyTime ust WHERE ust.userId = :userId")
    Long sumTotalSecondsByUserId(@Param("userId") Long userId);
}