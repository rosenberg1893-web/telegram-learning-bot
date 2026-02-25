package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.repository.*;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
@Component
public class CourseNavigationHandler extends BaseHandler {
    private final KeyboardBuilder keyboardBuilder;

    public CourseNavigationHandler(TelegramBot telegramBot,
                                   UserSessionService sessionService,
                                   NavigationService navigationService,
                                   AdminUserRepository adminUserRepository, // добавить
                                   KeyboardBuilder keyboardBuilder) {
        super(telegramBot, sessionService, navigationService, adminUserRepository);
        this.keyboardBuilder = keyboardBuilder;
    }

    // ================== Публичные методы для диспетчера ==================
    public void handleMyCourses(Long userId, Integer messageId, int page) {
        showMyCourses(userId, messageId, page);
    }

    public void handleAllCourses(Long userId, Integer messageId, int page) {
        showAllCourses(userId, messageId, page);
    }

    public void handleCoursesPage(Long userId, Integer messageId, String source, int page) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setCurrentPage(page);
        sessionService.updateSessionContext(userId, context);

        switch (source) {
            case SOURCE_MY_COURSES:
                showMyCourses(userId, messageId, page);
                break;
            case SOURCE_ALL_COURSES:
                showAllCourses(userId, messageId, page);
                break;
            case SOURCE_SEARCH:
                String query = context.getSearchQuery();
                var result = navigationService.getFoundCoursesPage(query, page);
                String text = String.format(FORMAT_SEARCH_RESULTS,
                        query, page + 1, result.getTotalPages(), result.getTotalItems());
                InlineKeyboardMarkup keyboard = keyboardBuilder.buildCoursesKeyboard(result, userId, SOURCE_SEARCH, CALLBACK_SELECT_COURSE, true);
                editMessage(userId, messageId, text, keyboard);
                break;
            default:
                sendMainMenu(userId, messageId);
        }
    }

    public void handleSelectCourse(Long userId, Integer messageId, Long courseId) {
        clearMediaMessages(userId);
        UserContext context = sessionService.getCurrentContext(userId);
        context.setCurrentCourseId(courseId);
        context.setCurrentPage(0);
        BotState prevState = sessionService.getCurrentState(userId);
        context.setPreviousMenuState(prevState.name());
        context.setCoursesListSource(prevState.name());
        sessionService.updateSession(userId, BotState.COURSE_SECTIONS, context);
        showCourseSections(userId, messageId, courseId, 0);
    }

    public void handleSelectSection(Long userId, Integer messageId, Long sectionId) {
        clearMediaMessages(userId);
        UserContext context = sessionService.getCurrentContext(userId);

        // Запоминаем, с какой страницы пришли
        context.setPreviousSectionPage(context.getCurrentPage());

        context.setCurrentSectionId(sectionId);
        context.setCurrentPage(0); // для раздела
        context.setPreviousMenuState(sessionService.getCurrentState(userId).name());
        sessionService.updateSession(userId, BotState.SECTION_TOPICS, context);
        showSectionTopics(userId, messageId, sectionId, 0);
    }

    public void handleSelectTopic(Long userId, Integer messageId, Long topicId) {
        clearMediaMessages(userId);
        UserContext context = sessionService.getCurrentContext(userId);

        // Запоминаем, с какой страницы пришли
        context.setPreviousTopicPage(context.getCurrentPage());

        List<Block> blocks = navigationService.getTopicBlocksWithQuestions(topicId);
        if (blocks.isEmpty()) {
            // Вместо главного меню покажем клавиатуру с возвратом к списку тем
            String text = MSG_TOPIC_NO_BLOCKS;
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                    new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_BACK).callbackData(CALLBACK_BACK_TO_TOPICS)}
            );
            if (messageId != null) {
                editMessage(userId, messageId, text, keyboard);
            } else {
                sendMessage(userId, text, keyboard);
            }
            return;
        }

        List<Long> blockIds = blocks.stream().map(Block::getId).toList();
        context.setCurrentTopicBlockIds(blockIds);
        context.setCurrentBlockIndex(0);
        context.setCurrentBlockQuestionIndex(-1);
        context.setCurrentTopicId(topicId);
        context.setTestMode(false);
        context.setPreviousMenuState(sessionService.getCurrentState(userId).name());
        context.setCorrectAnswers(0);
        context.setWrongAnswers(0);

        Long firstBlockId = blockIds.get(0);
        List<Question> questions = navigationService.getQuestionsForBlock(firstBlockId);
        context.setCurrentBlockQuestionIds(questions.stream().map(Question::getId).toList());

        sessionService.updateSession(userId, BotState.TOPIC_LEARNING, context);
        showBlockContent(userId, messageId, firstBlockId);
    }

    public void handleSectionsPage(Long userId, Integer messageId, Long courseId, int page) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setCurrentPage(page);                       // <-- сохраняем текущую страницу
        sessionService.updateSessionContext(userId, context);
        showCourseSections(userId, messageId, courseId, page);
    }

    public void handleTopicsPage(Long userId, Integer messageId, Long sectionId, int page) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setCurrentPage(page);                       // <-- сохраняем текущую страницу
        sessionService.updateSessionContext(userId, context);
        showSectionTopics(userId, messageId, sectionId, page);
    }

    public void handleNextBlock(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        int currentIdx = context.getCurrentBlockIndex();
        List<Long> blockIds = context.getCurrentTopicBlockIds();

        if (currentIdx < blockIds.size() - 1) {
            Long nextBlockId = blockIds.get(currentIdx + 1);
            List<Question> questions = navigationService.getQuestionsForBlock(nextBlockId);
            context.setCurrentBlockQuestionIds(questions.stream().map(Question::getId).toList());
            context.setCurrentBlockIndex(currentIdx + 1);
            context.setCurrentBlockQuestionIndex(-1);
            sessionService.updateSessionContext(userId, context);
            showBlockContent(userId, messageId, nextBlockId);
        } else {
            int correct = context.getCorrectAnswers();
            int wrong = context.getWrongAnswers();
            int total = correct + wrong;
            String stats = String.format(FORMAT_TOPIC_COMPLETED,
                    correct, wrong, total);

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                    new InlineKeyboardButton(BUTTON_BACK).callbackData(CALLBACK_BACK_TO_TOPICS)
            );

            if (messageId != null) {
                editMessage(userId, messageId, stats, keyboard);
            } else {
                sendMessage(userId, stats, keyboard);
            }

            context.setCorrectAnswers(0);
            context.setWrongAnswers(0);
            sessionService.updateSessionContext(userId, context);
        }
    }

    public void handlePrevBlock(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        int currentIdx = context.getCurrentBlockIndex();

        if (currentIdx > 0) {
            Long prevBlockId = context.getCurrentTopicBlockIds().get(currentIdx - 1);
            List<Question> questions = navigationService.getQuestionsForBlock(prevBlockId);
            context.setCurrentBlockQuestionIds(questions.stream().map(Question::getId).toList());
            context.setCurrentBlockIndex(currentIdx - 1);
            context.setCurrentBlockQuestionIndex(-1);
            sessionService.updateSessionContext(userId, context);
            showBlockContent(userId, messageId, prevBlockId);
        } else {
            handleBackToSections(userId, messageId);
        }
    }
    public void promptSearch(Long userId, Integer messageId) {
        String text = MSG_SEARCH_PROMPT;
        if (messageId != null) {
            editMessage(userId, messageId, text, createCancelKeyboard());
        } else {
            sendMessage(userId, text, createCancelKeyboard());
        }
        sessionService.updateSessionState(userId, BotState.AWAITING_SEARCH_QUERY);
    }

    public void handleSearchQuery(Long userId, String query) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setSearchQuery(query);
        sessionService.updateSessionContext(userId, context);

        var result = navigationService.getFoundCoursesPage(query, 0);
        if (result.getItems().isEmpty()) {
            sendMessage(userId, "😕 По запросу \"" + query + "\" ничего не найдено.", createSearchNotFoundKeyboard());
            return;
        }
        String text = String.format("🔍 Результаты поиска по запросу «%s» (страница 1 из %d) – найдено %d курсов.",
                query, result.getTotalPages(), result.getTotalItems());
        InlineKeyboardMarkup keyboard = keyboardBuilder.buildCoursesKeyboard(result, userId, SOURCE_SEARCH, CALLBACK_SELECT_COURSE, true);
        sendMessage(userId, text, keyboard);
        sessionService.updateSessionState(userId, BotState.SEARCH_COURSES);
    }
    public void handleBackToCourses(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);   // добавить
        if (context.getCurrentCourseId() != null) {
            navigationService.updateCourseLastAccessedOnExit(userId, context.getCurrentCourseId());
        }
        clearMediaMessages(userId);

        String source = context.getCoursesListSource();
        log.info("handleBackToCourses: userId={}, source={}", userId, source);

        if (SOURCE_MY_COURSES.equals(source)) {
            showMyCourses(userId, messageId, 0);
        } else if (SOURCE_ALL_COURSES.equals(source)) {
            showAllCourses(userId, messageId, 0);
        } else if (SOURCE_SEARCH.equals(source)) {
            String query = context.getSearchQuery();
            var result = navigationService.getFoundCoursesPage(query, 0);
            String text = "Результаты поиска (страница 1):";
            InlineKeyboardMarkup keyboard = keyboardBuilder.buildCoursesKeyboard(result, userId, SOURCE_SEARCH, CALLBACK_SELECT_COURSE, true);
            editMessage(userId, messageId, text, keyboard);
            sessionService.updateSessionState(userId, BotState.SEARCH_COURSES);
        } else {
            showAllCourses(userId, messageId, 0);
        }
    }

    public void handleBackToSections(Long userId, Integer messageId) {
        clearMediaMessages(userId);
        UserContext context = sessionService.getCurrentContext(userId);

        if (context.getCurrentCourseId() != null) {
            int page = context.getPreviousSectionPage() != null ? context.getPreviousSectionPage() : 0;
            showCourseSections(userId, messageId, context.getCurrentCourseId(), page);
            sessionService.updateSessionState(userId, BotState.COURSE_SECTIONS);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    public void handleBackToTopics(Long userId, Integer messageId) {
        clearMediaMessages(userId);
        UserContext context = sessionService.getCurrentContext(userId);

        if (context.getCurrentSectionId() != null) {
            int page = context.getPreviousTopicPage() != null ? context.getPreviousTopicPage() : 0;
            showSectionTopics(userId, messageId, context.getCurrentSectionId(), page);
            sessionService.updateSessionState(userId, BotState.SECTION_TOPICS);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    // ================== Внутренние методы навигации ==================
    private void showMyCourses(Long userId, Integer messageId, int page) {
        var result = navigationService.getMyCoursesPage(userId, page);
        if (result.getItems().isEmpty()) {
            String text = MSG_NO_MY_COURSES;
            if (messageId != null) {
                editMessage(userId, messageId, text, createBackToMainKeyboard());
            } else {
                sendMessage(userId, text, createBackToMainKeyboard());
            }
            return;
        }
        String text = String.format(FORMAT_MY_COURSES_HEADER,
                page + 1, result.getTotalPages(), result.getTotalItems());
        InlineKeyboardMarkup keyboard = keyboardBuilder.buildCoursesKeyboard(result, userId, CALLBACK_MY_COURSES, CALLBACK_SELECT_COURSE, true);
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    private void showAllCourses(Long userId, Integer messageId, int page) {
        var result = navigationService.getAllCoursesPage(page);
        if (result.getItems().isEmpty()) {
            String text = MSG_NO_COURSES;
            if (messageId != null) {
                editMessage(userId, messageId, text, createBackToMainKeyboard());
            } else {
                sendMessage(userId, text, createBackToMainKeyboard());
            }
            return;
        }
        String text = String.format(FORMAT_ALL_COURSES_HEADER,
                page + 1, result.getTotalPages(), result.getTotalItems());
        InlineKeyboardMarkup keyboard = keyboardBuilder.buildCoursesKeyboard(result, userId, CALLBACK_ALL_COURSES, CALLBACK_SELECT_COURSE, true);
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    private void showCourseSections(Long userId, Integer messageId, Long courseId, int page) {
        var result = navigationService.getSectionsPage(courseId, page);
        String courseTitle = navigationService.getCourseTitle(courseId);
        String courseDescription = navigationService.getCourseDescription(courseId);
        String lastAccessedStr = formatLastAccessed(navigationService.getCourseLastAccessed(userId, courseId));

        if (messageId != null && page == 0) {
            String text = String.format(FORMAT_COURSE_SECTIONS_HEADER,
                    courseTitle, courseDescription, lastAccessedStr, page + 1, result.getTotalPages(), result.getTotalItems());
            InlineKeyboardMarkup keyboard = keyboardBuilder.buildSectionsKeyboard(result, userId, courseId, true, CALLBACK_SELECT_SECTION);
            editMessage(userId, messageId, text, keyboard);
        } else if (messageId != null) {
            String text = String.format(FORMAT_SECTIONS_HEADER,
                    courseTitle, page + 1, result.getTotalPages(), result.getTotalItems(), lastAccessedStr);
            InlineKeyboardMarkup keyboard = keyboardBuilder.buildSectionsKeyboard(result, userId, courseId, true, CALLBACK_SELECT_SECTION);
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, String.format(FORMAT_COURSE_HEADER, courseTitle, courseDescription, lastAccessedStr));
            String text = String.format("📌 **Разделы** курса «%s» (страница %d из %d) – всего %d разделов.\nВыберите раздел.",
                    courseTitle, page + 1, result.getTotalPages(), result.getTotalItems());
            InlineKeyboardMarkup keyboard = keyboardBuilder.buildSectionsKeyboard(result, userId, courseId, true, CALLBACK_SELECT_SECTION);
            sendMessage(userId, text, keyboard);
        }
    }

    private void showSectionTopics(Long userId, Integer messageId, Long sectionId, int page) {
        var result = navigationService.getTopicsPage(sectionId, page);
        String sectionTitle = navigationService.getSectionTitle(sectionId);
        String sectionDescription = navigationService.getSectionDescription(sectionId);

        Instant lastAccessed = navigationService.getSectionLastAccessed(userId, sectionId);
        String lastAccessedStr = formatLastAccessed(lastAccessed);

        if (messageId != null && page == 0) {
            String text = String.format(FORMAT_TOPICS_HEADER,
                    sectionTitle, sectionDescription, lastAccessedStr, page + 1, result.getTotalPages(), result.getTotalItems());
            InlineKeyboardMarkup keyboard = keyboardBuilder.buildTopicsKeyboard(result, userId, sectionId, true, CALLBACK_SELECT_TOPIC);
            editMessage(userId, messageId, text, keyboard);
            navigationService.updateSectionLastAccessed(userId, sectionId);
        } else if (messageId != null) {
            String text = String.format(FORMAT_TOPICS_HEADER2,
                    sectionTitle, page + 1, result.getTotalPages(), result.getTotalItems(), lastAccessedStr);
            InlineKeyboardMarkup keyboard = keyboardBuilder.buildTopicsKeyboard(result, userId, sectionId, true, CALLBACK_SELECT_TOPIC);
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, String.format(FORMAT_SECTION_HEADER, sectionTitle, sectionDescription, lastAccessedStr));
            String text = String.format("📌 **Темы** раздела «%s» (страница %d из %d) – всего %d тем.\nВыберите тему.",
                    sectionTitle, page + 1, result.getTotalPages(), result.getTotalItems());
            InlineKeyboardMarkup keyboard = keyboardBuilder.buildTopicsKeyboard(result, userId, sectionId, true, CALLBACK_SELECT_TOPIC);
            sendMessage(userId, text, keyboard);
            if (page == 0) {
                navigationService.updateSectionLastAccessed(userId, sectionId);
            }
        }
    }

    public void showBlockContent(Long userId, Integer messageId, Long blockId) {
        UserContext context = sessionService.getCurrentContext(userId); // добавить
        navigationService.getBlockWithImages(blockId).ifPresentOrElse(block -> {
            String text = block.getTextContent();
            InlineKeyboardMarkup keyboard = keyboardBuilder.buildBlockNavigationKeyboard(context);
            if (messageId != null) {
                editMessage(userId, messageId, text, keyboard);
            } else {
                sendMessage(userId, text, keyboard);
            }
            sendMediaGroup(userId, block.getImages());
        }, () -> {
            if (messageId != null) {
                editMessage(userId, messageId, MSG_BLOCK_NOT_FOUND, createBackToMainKeyboard());
            } else {
                sendMessage(userId, MSG_BLOCK_NOT_FOUND, createBackToMainKeyboard());
            }
        });
    }
}