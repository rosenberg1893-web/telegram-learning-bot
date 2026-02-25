package com.lbt.telegram_learning_bot.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotPoller {

    private final TelegramBot bot;          // внедряем готовый бин из конфигурации
    private final TelegramBotHandler handler;

    @PostConstruct
    public void init() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                handler.handle(update);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }, Throwable::printStackTrace);
        log.info("Telegram bot polling started");
    }

    @PreDestroy
    public void destroy() {
        bot.removeGetUpdatesListener();
    }
}