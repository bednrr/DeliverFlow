package protocol.requests;

import model.ParcelSize;

public record CreateParcelRequest(
        String token,
        String title,
        String description,
        ParcelSize size,
        double weightKg,
        String receiverName,
        String receiverPhone,
        String senderAddressText,
        long senderMapPointId,
        String receiverAddressText,
        long receiverMapPointId,
        boolean saveSenderAddress,
        String senderAddressName,
        boolean saveReceiverAddress,
        String receiverAddressName
) {
}
