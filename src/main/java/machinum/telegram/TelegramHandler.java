package machinum.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.scheduler.ActionHandler;

@Slf4j
@RequiredArgsConstructor
public class TelegramHandler implements ActionHandler {

    private final TelegramService telegramService;

    @Override
    public void handle(ActionContext context) {
        throw new IllegalStateException("Not implemented!");
    }

}
