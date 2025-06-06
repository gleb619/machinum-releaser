package machinum.telegram;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.Objects;

@Value
@AllArgsConstructor
@Builder(toBuilder = true)
public class TelegramProperties {

    String token;

    String testChatId;

    String mainChatId;

    String channelName;

    String channelLink;

    public String getChatId(ChatType chatType) {
        return switch (chatType) {
            case MAIN -> getMainChatId();
            case TEST -> getTestChatId();
        };
    }

    public enum ChatType {

        MAIN, TEST;

        public static ChatType of(String value) {
            if(value.equalsIgnoreCase(MAIN.name())) {
                return MAIN;
            } else {
                return TEST;
            }
        }

    }

}
