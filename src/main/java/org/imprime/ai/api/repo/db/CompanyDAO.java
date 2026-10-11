package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.dto.CompanyAndAddress;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Repository
public class CompanyDAO {
    private static final double DEFAULT_RADIUS_METERS = 50_000d;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CompanyDAO(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CompanyAndAddress> findNearestCompaniesByAddress(Double latitude, Double longitude, int limit) {
        if (latitude == null || longitude == null || limit <= 0) {
            return List.of();
        }

        String sql = """
                SELECT
                    c.company_id AS company_id,
                    c.name AS company_name,
                    c.document AS company_document,
                    c.document_type AS company_document_type,
                    c.address_id AS company_address_id,
                    c.owner_id AS company_owner_id,
                    c.guid AS company_guid,
                    c.status AS company_status,
                    c.create_dt AS company_create_dt,
                    c.create_user AS company_create_user,
                    c.update_dt AS company_update_dt,
                    c.update_user AS company_update_user,
                    a.address_id AS address_id,
                    a.owner_id AS address_owner_id,
                    a.owner_type AS address_owner_type,
                    a.country AS address_country,
                    a.state AS address_state,
                    a.city AS address_city,
                    a.zip_code AS address_zip_code,
                    a.latitude AS address_latitude,
                    a.longitude AS address_longitude,
                    a.neighborhood AS address_neighborhood,
                    a.address_line_1 AS address_line_1,
                    a.address_line_2 AS address_line_2,
                    a.default_address AS address_default_address,
                    a.guid AS address_guid,
                    a.status AS address_status,
                    a.create_dt AS address_create_dt,
                    a.create_user AS address_create_user,
                    a.update_dt AS address_update_dt,
                    a.update_user AS address_update_user
                FROM company c
                JOIN address a
                  ON a.owner_id = c.company_id
                 AND a.owner_type = 'C'
                 AND a.default_address = true
                 AND a.status = 'A'
                 AND a.latitude IS NOT NULL
                 AND a.longitude IS NOT NULL
                WHERE ST_DWithin(
                    ST_SetSRID(ST_MakePoint(a.longitude, a.latitude), 4326)::geography,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                    :radiusMeters
                )
                ORDER BY ST_Distance(
                    ST_SetSRID(ST_MakePoint(a.longitude, a.latitude), 4326)::geography,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                ) ASC
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("radiusMeters", DEFAULT_RADIUS_METERS)
                .addValue("limit", limit);

        return jdbcTemplate.query(sql, params, this::mapRow);
    }

    private CompanyAndAddress mapRow(ResultSet rs, int rowNum) throws SQLException {
        Company company = new Company();
        company.setId(rs.getLong("company_id"));
        company.setName(rs.getString("company_name"));
        company.setDocument(rs.getString("company_document"));
        company.setDocumentType(parseDocumentType(rs.getString("company_document_type")));
        company.setAddressId(rs.getLong("company_address_id"));
        company.setOwnerId(rs.getLong("company_owner_id"));
        company.setGuid(rs.getString("company_guid"));
        company.setStatus(parseStatus(rs.getString("company_status")));
        company.setCreateDt(rs.getTimestamp("company_create_dt") == null ? null : rs.getTimestamp("company_create_dt").toInstant());
        company.setCreateUser(rs.getString("company_create_user"));
        if (rs.getTimestamp("company_update_dt") != null) {
            company.setUpdateDt(rs.getTimestamp("company_update_dt").toInstant());
        }
        company.setUpdateUser(rs.getString("company_update_user"));

        Address address = new Address();
        address.setId(rs.getLong("address_id"));
        address.setOwnerId(rs.getLong("address_owner_id"));
        address.setOwnerType(parseEntityType(rs.getString("address_owner_type")));
        address.setCountry(rs.getString("address_country"));
        address.setState(rs.getString("address_state"));
        address.setCity(rs.getString("address_city"));
        address.setZipCode(rs.getString("address_zip_code"));
        address.setLatitude(rs.getObject("address_latitude") == null ? null : rs.getDouble("address_latitude"));
        address.setLongitude(rs.getObject("address_longitude") == null ? null : rs.getDouble("address_longitude"));
        address.setNeighborhood(rs.getString("address_neighborhood"));
        address.setAddressLine1(rs.getString("address_line_1"));
        address.setAddressLine2(rs.getString("address_line_2"));
        address.setDefaultAddress(rs.getBoolean("address_default_address"));
        address.setGuid(rs.getString("address_guid"));
        address.setStatus(parseStatus(rs.getString("address_status")));
        address.setCreateDt(rs.getTimestamp("address_create_dt") == null ? null : rs.getTimestamp("address_create_dt").toInstant());
        address.setCreateUser(rs.getString("address_create_user"));
        address.setUpdateDt(rs.getTimestamp("address_update_dt") == null ? null : rs.getTimestamp("address_update_dt").toInstant());
        address.setUpdateUser(rs.getString("address_update_user"));

        return new CompanyAndAddress(company, address);
    }

    private DocumentType parseDocumentType(String code) {
        if (code == null) {
            return null;
        }

        DocumentType.Converter converter = new DocumentType.Converter();
        return converter.convertToEntityAttribute(code);
    }

    private EntityType parseEntityType(String code) {
        if (code == null) {
            return null;
        }

        return Arrays.stream(EntityType.values())
                .filter(value -> value.getCode().equals(code))
                .findFirst()
                .orElse(null);
    }

    private StatusCd parseStatus(String code) {
        if (code == null) {
            return null;
        }

        return Arrays.stream(StatusCd.values())
                .filter(value -> value.getCode().equals(code))
                .findFirst()
                .orElse(null);
    }
}
