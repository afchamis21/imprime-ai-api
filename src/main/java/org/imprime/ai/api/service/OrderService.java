package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.order.StartOrderRequest;
import org.imprime.ai.api.http.response.StartOrderResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.CompanyChoiceDTO;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final AddressService addressService;
    private final CompanyService companyService;
    private final FileAssetService fileAssetService;
    private final OrderRepository orderRepository;
    private final OrderStatusService orderStatusService;

    public StartOrderResponse start(MultipartFile model, StartOrderRequest request) {
        // 1. Validate the Address Exists and belongs to the user
        Optional<Address> optionalAddress = addressService.findByGuid(request.addressGuid());
        if (optionalAddress.isEmpty()) {
            log.warn("No address found for given guid: {}", request.addressGuid());
            throw new BadRequestException(MessageCd.INVALID_ADDRESS_GUID);
        }

        User caller = ServiceContext.getUserOrThrow();
        Address address = optionalAddress.get();

        if (!EntityType.USER.equals(address.getOwnerType()) || !caller.getId().equals(address.getOwnerId())) {
            log.warn("Address [{}. Owner {} | {}] does not belong to user [{}]", address.getId(), address.getOwnerId(), address.getOwnerType(), caller.getId());
            throw new ForbiddenException();
        }

        if (!StatusCd.ACTIVE.equals(address.getStatus())) {
            log.warn("Address [{}] is not Active [{}]",  address.getId(), address.getStatus());
            throw new BadRequestException(MessageCd.ADDRESS_NOT_ACTIVE);
        }

        // 2. Validate the model
        // 3. Upload the model
        // 3.1. If error get out and return error to the UI
        // 4. Create the FileAsset entity
        // 4.1. If error delete the uploaded model and return error to the UI
        FileAsset fileAsset = fileAssetService.upload(model, FileAsset.AssetType.MODEL);

        // 5. Create the Order
        // 5.1 If error curl up and cry. Then delete the asset and the uploaded model and return error to the UI
        Order order = new Order();
        try {
            order.setFromAddressId(address.getId());
            order.setBuyerId(caller.getId());
            order.setFileAssetId(fileAsset.getId());
            order = orderRepository.save(order);
        } catch (Exception e) {
            log.error("Error persisting order! Rolling back File Asset", e);
            fileAssetService.deleteForever(fileAsset);
            throw new InternalErrorException();
        }

        // 5.2 Create the Created Status
        orderStatusService.move(order, OrderStatusCd.CREATED);
        // 5.3 Create the Quoting Status
        orderStatusService.move(order, OrderStatusCd.QUOTING);

        // 6. Fetch list of makers
        // 6.1 If error return the OrderDTO with the OrderGUID to the UI so they can try to search makers again I guess
        // 7. Return DTO containing the OrderGUID + Ship To Address + Maker List to the UI

        try {
            List<CompanyChoiceDTO> companyChoiceDTOS = companyService.findCompaniesForAddress(address, 10);
            return new StartOrderResponse(order.getGuid(), AddressDTO.from(address), companyChoiceDTOS);
        } catch (Exception e) {
            log.error("Error searching companies for order. Returning Order Guid to the UI", e);
            ServiceContext.getContext().addMessage(MessageCd.ERROR_LOADING_MAKERS);
            return new StartOrderResponse(order.getGuid(), AddressDTO.from(address), List.of());
        }
    }

    // Fetch Order Snapshot

    // Fetch Order Chats -> might exist on chat service
    //  This will be split -> If order is in QUOTING status return active chats + potential makers
    //  If on any other status, just return the one Active chat

    // Fetch Maker List -> might exist on chat service
}
