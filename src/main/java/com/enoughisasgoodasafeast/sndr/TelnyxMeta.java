package com.enoughisasgoodasafeast.sndr;

import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.datagen.KnownData;
import com.enoughisasgoodasafeast.sndr.server.model.CreateMessageRequest;

/**
 * The (cached) object used to construct the payload sent to Telnyx for delivery as an SMS message.
 * @param messagingProfileId the vendor specific "account" id required by their API.
 */
public record TelnyxMeta(String messagingProfileId, String bearerAPIKey) implements GatewayMeta {

    @Override
    public CreateMessageRequest toGatewayMessage(Message message) {
        var cmr = new CreateMessageRequest();
        cmr.setFrom(message.from());
        cmr.setMessagingProfileId(KnownData.TELNYX_MESSAGING_PROFILE_IDS[0]);
        cmr.setTo(message.to());
        cmr.setText(message.text());
        cmr.setType(CreateMessageRequest.TypeEnum.SMS);

                //String from, String messagingProfileId, String to, String text,
//                message.from(), KnownData.TELNYX_MESSAGING_PROFILE_IDS[0], message.to(), message.text(),
                // String subject, List<String> mediaUrls, String webhookUrl, String webhookFailoverUrl, Boolean useProfileWebhooks,
//                null, null, null, null, true,
                // CreateMessageRequest.TypeEnum type, Boolean autoDetect, Object sendAt, CreateMessageRequest.EncodingEnum encoding
//                CreateMessageRequest.TypeEnum.SMS, true, null, CreateMessageRequest.EncodingEnum.AUTO
//        );
        return cmr;
    }

}

