package service;

import model.ClientAddress;
import model.MapPoint;
import repository.ClientAddressRepository;
import util.ValidationException;

import java.util.List;

public class AddressService {
    private final ClientAddressRepository addressRepository;
    private final MapService mapService;

    public AddressService(ClientAddressRepository addressRepository, MapService mapService) {
        this.addressRepository = addressRepository;
        this.mapService = mapService;
    }

    public ClientAddress saveAddress(long userId, Long id, String name, String addressText, long mapPointId, String notes) {
        String normalizedName = requireNotBlank(name, "Nazwa adresu nie może być pusta.");
        String normalizedAddress = requireNotBlank(addressText, "Adres nie może być pusty.");
        Long checkedId = id == null || id == 0 ? null : id;
        if (addressRepository.nameExistsForUser(userId, normalizedName, checkedId)) {
            throw new ValidationException("Masz już zapisany adres o tej nazwie.");
        }
        MapPoint point = mapService.resolveOrCreatePoint(normalizedAddress, mapPointId);
        ClientAddress address = new ClientAddress();
        address.setId(id == null ? 0 : id);
        address.setUserId(userId);
        address.setName(normalizedName);
        address.setAddressText(normalizedAddress);
        address.setMapPointId(point.getId());
        address.setNotes(notes == null ? "" : notes.trim());
        if (id == null || id == 0) {
            return addressRepository.save(address);
        }
        addressRepository.update(address);
        return addressRepository.findById(id).orElseThrow();
    }

    public List<ClientAddress> listForUser(long userId) {
        return addressRepository.findByUserId(userId);
    }

    public void deleteAddress(long userId, long addressId) {
        addressRepository.findById(addressId)
                .filter(address -> address.getUserId() == userId)
                .orElseThrow(() -> new ValidationException("Nie znaleziono adresu."));
        addressRepository.delete(addressId, userId);
    }

    private String requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }
}
