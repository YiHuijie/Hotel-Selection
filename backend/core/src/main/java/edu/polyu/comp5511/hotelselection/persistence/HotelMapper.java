package edu.polyu.comp5511.hotelselection.persistence;

import edu.polyu.comp5511.hotelselection.domain.Hotel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface HotelMapper {
    @Select("""
            SELECT h.id, h.name, h.city, h.address, h.rating,
                   r.pricePerNight, r.breakfast,
                   h.amenities, h.keywords, d.longitude, d.latitude,
                   h.image, h.province, h.district, d.opening_year AS openingYear,
                   d.renovation_year AS renovationYear, d.phone, d.gym, d.laundry, d.parking,
                   d.luggage_storage AS luggageStorage, d.robot_service AS robotService,
                   d.restaurant, d.taxi_service AS taxiService,
                   d.meeting_room AS meetingRoom, d.accessibility
            FROM hotel h
            JOIN (
                SELECT hotel_id, MIN(max_nightly_price) AS pricePerNight,
                       MAX(breakfast_status) AS breakfast
                FROM (
                    SELECT hotel_id, room_name, MAX(price_per_night) AS max_nightly_price,
                           CASE
                               WHEN COUNT(breakfast) = COUNT(*)
                                    AND MIN(CASE WHEN breakfast = TRUE THEN 1 ELSE 0 END) = 1 THEN 1
                               WHEN COUNT(breakfast) = COUNT(*) THEN 0
                               ELSE NULL
                           END AS breakfast_status
                    FROM room_offer
                    WHERE available_on >= #{checkIn} AND available_on < #{checkOut}
                    GROUP BY hotel_id, room_name
                    HAVING COUNT(DISTINCT available_on) = #{nightCount}
                ) complete_stays
                GROUP BY hotel_id
            ) r ON r.hotel_id = h.id
            LEFT JOIN hotel_detail d ON d.hotel_id = h.id
            WHERE LOWER(h.city) = LOWER(#{city})
            ORDER BY h.id
            """)
    List<Hotel> findAvailable(
            @Param("city") String city,
            @Param("checkIn") java.time.LocalDate checkIn,
            @Param("checkOut") java.time.LocalDate checkOut,
            @Param("nightCount") long nightCount);

    @Select("""
            SELECT h.id, h.name, h.city, h.address, h.rating,
                   NULL AS pricePerNight, NULL AS breakfast,
                   h.amenities, h.keywords, d.longitude, d.latitude,
                   h.image, h.province, h.district, d.opening_year AS openingYear,
                   d.renovation_year AS renovationYear, d.phone, d.gym, d.laundry, d.parking,
                   d.luggage_storage AS luggageStorage, d.robot_service AS robotService,
                   d.restaurant, d.taxi_service AS taxiService,
                   d.meeting_room AS meetingRoom, d.accessibility
            FROM hotel h
            LEFT JOIN hotel_detail d ON d.hotel_id = h.id
            WHERE h.id = #{hotelId}
            """)
    Optional<Hotel> findById(@Param("hotelId") long hotelId);
}
