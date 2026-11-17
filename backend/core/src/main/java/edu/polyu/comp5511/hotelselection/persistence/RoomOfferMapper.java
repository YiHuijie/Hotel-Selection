package edu.polyu.comp5511.hotelselection.persistence;

import edu.polyu.comp5511.hotelselection.domain.RoomOffer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RoomOfferMapper {
    @Select("""
            SELECT id, hotel_id AS hotelId, room_name, price_per_night, breakfast,
                   available_on AS availableFrom, available_on AS availableTo,
                   hair_dryer AS hairDryer, towels
            FROM room_offer
            WHERE hotel_id = #{hotelId}
            ORDER BY price_per_night, id
            """)
    List<RoomOffer> findByHotelId(@Param("hotelId") long hotelId);

    @Select({
            "<script>",
            "SELECT MIN(id) AS id, hotel_id AS hotelId, room_name AS roomName,",
            "MAX(price_per_night) AS pricePerNight,",
            "CASE WHEN COUNT(breakfast) = COUNT(*)",
            "AND MIN(CASE WHEN breakfast = TRUE THEN 1 ELSE 0 END) = 1 THEN TRUE",
            "WHEN COUNT(breakfast) = COUNT(*) THEN FALSE ELSE NULL END AS breakfast,",
            "MIN(available_on) AS availableFrom, MAX(available_on) AS availableTo,",
            "CASE WHEN COUNT(hair_dryer) = COUNT(*)",
            "AND MIN(CASE WHEN hair_dryer = TRUE THEN 1 ELSE 0 END) = 1 THEN TRUE",
            "WHEN COUNT(hair_dryer) = COUNT(*) THEN FALSE ELSE NULL END AS hairDryer,",
            "CASE WHEN COUNT(towels) = COUNT(*)",
            "AND MIN(CASE WHEN towels = TRUE THEN 1 ELSE 0 END) = 1 THEN TRUE",
            "WHEN COUNT(towels) = COUNT(*) THEN FALSE ELSE NULL END AS towels",
            "FROM room_offer",
            "WHERE hotel_id IN",
            "<foreach item='hotelId' collection='hotelIds' open='(' separator=',' close=')'>",
            "#{hotelId}",
            "</foreach>",
            "AND available_on &gt;= #{checkIn} AND available_on &lt; #{checkOut}",
            "GROUP BY hotel_id, room_name",
            "HAVING COUNT(DISTINCT available_on) = #{nightCount}",
            "ORDER BY hotel_id, pricePerNight, room_name",
            "</script>"
    })
    List<RoomOffer> findAvailableForStay(
            @Param("hotelIds") List<Long> hotelIds,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("nightCount") long nightCount);
}
