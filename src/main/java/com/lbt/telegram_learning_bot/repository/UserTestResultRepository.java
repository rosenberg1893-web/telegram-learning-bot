package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.UserTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTestResultRepository extends JpaRepository<UserTestResult, Long> {
    Optional<UserTestResult> findByUserIdAndTestTypeAndTestId(Long userId, String testType, Long testId);


    @Query("SELECT t.testId, t.correctCount, t.wrongCount FROM UserTestResult t WHERE t.userId = :userId AND t.testType = :testType AND t.testId IN :testIds")
    List<Object[]> findTestResultsByUserAndTestIds(@Param("userId") Long userId, @Param("testType") String testType, @Param("testIds") List<Long> testIds);
}