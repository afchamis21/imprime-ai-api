package org.imprime.ai.api.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.dto.Owner;
import org.imprime.ai.api.model.enums.EntityType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AddressDAO {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EntityType.Converter entityTypeConverter = new EntityType.Converter();

    private final RowMapper<Address> addressRowMapper = (rs, ignored) -> {
        Address address = new Address();

        address.setId(rs.getLong("ADDRESS_ID"));
        address.setOwnerId(rs.getLong("OWNER_ID"));

        address.setOwnerType(entityTypeConverter.convertToEntityAttribute(rs.getString("OWNER_TYPE")));

        address.setCountry(rs.getString("COUNTRY"));
        address.setState(rs.getString("STATE"));
        address.setCity(rs.getString("CITY"));
        address.setZipCode(rs.getString("ZIP_CODE"));
        address.setNeighborhood(rs.getString("NEIGHBORHOOD"));
        address.setAddressLine1(rs.getString("ADDRESS_LINE_1"));
        address.setAddressLine2(rs.getString("ADDRESS_LINE_2"));

        address.setDefaultAddress(rs.getBoolean("DEFAULT_ADDRESS"));

        return address;
    };

    private static final String SEARCH_ADDRESS_BY_OWNER = """
            SELECT *
            FROM ADDRESS a
            WHERE a.STATUS = 'A'
              AND (a.OWNER_ID, a.OWNER_TYPE) IN (%s)
            OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """;

    public List<Address> searchAddressesByOwner(
            List<Owner<Long>> owners,
            Integer page,
            Integer size) {

        if (owners == null || owners.isEmpty()) {
            return List.of();
        }

        page = page == null || page < 0 ? 0 : page;
        size = size == null || size < 0 ? 10 : size;

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("offset", page * size);
        params.addValue("size", size);

        List<String> tuples = new ArrayList<>();

        for (int i = 0; i < owners.size(); i++) {
            Owner<Long> owner = owners.get(i);

            String idParam = "ownerId" + i;
            String typeParam = "ownerType" + i;

            tuples.add("(" + ":" + idParam + ", :" + typeParam + ")");

            params.addValue(idParam, owner.ownerId());
            params.addValue(
                    typeParam,
                    entityTypeConverter.convertToDatabaseColumn(owner.entityType())
            );
        }

        String sql = SEARCH_ADDRESS_BY_OWNER.formatted(
                String.join(", ", tuples)
        );

        try {
            return jdbcTemplate.query(
                    sql,
                    params,
                    addressRowMapper
            );
        } catch (Exception e) {
            log.error("Error searching addresses by owner", e);
            return List.of();
        }
    }
}
