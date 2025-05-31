package machinum.telegram;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@AllArgsConstructor
@Builder(toBuilder = true)
public class TelegramProperties {

    String token;

    String chatId;

    String channelName;

    String channelLink;

}
