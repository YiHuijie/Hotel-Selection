package edu.polyu.comp5511.hotelselection.persistence;

import edu.polyu.comp5511.hotelselection.domain.HotelReview;
import edu.polyu.comp5511.hotelselection.domain.ReviewForMatching;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewMapper {
    @Insert("""
            INSERT INTO review (hotel_id, user_id, rating, content, created_at)
            VALUES (#{hotelId}, #{userId}, #{rating}, #{comment}, CURRENT_TIMESTAMP)
            """)
    int insert(
            @Param("hotelId") long hotelId,
            @Param("userId") String userId,
            @Param("rating") int rating,
            @Param("comment") String comment);

    @Select({
            "<script>",
            "SELECT hotel_id AS hotelId, source_review_id AS reviewId, rating, content,",
            "created_at AS createdAt FROM review",
            "WHERE hotel_id IN",
            "<foreach item='hotelId' collection='hotelIds' open='(' separator=',' close=')'>",
            "#{hotelId}",
            "</foreach>",
            "ORDER BY created_at DESC LIMIT 100",
            "</script>"
    })
    List<HotelReview> findReviewsForHotels(@Param("hotelIds") List<Long> hotelIds);

    @Select({
            "<script>",
            "SELECT id, hotel_id AS hotelId, content, created_at AS createdAt,",
            "CASE WHEN user_id = #{userId} THEN TRUE ELSE FALSE END AS own FROM review",
            "WHERE hotel_id IN",
            "<foreach item='hotelId' collection='hotelIds' open='(' separator=',' close=')'>",
            "#{hotelId}",
            "</foreach>",
            "ORDER BY id",
            "</script>"
    })
    List<ReviewForMatching> findReviewsForMatching(
            @Param("hotelIds") List<Long> hotelIds, @Param("userId") String userId);

    @Select("""
            SELECT hotel_id AS hotelId, source_review_id AS reviewId, rating, content,
                   created_at AS createdAt
            FROM review
            WHERE hotel_id = #{hotelId}
            ORDER BY created_at DESC, id
            """)
    List<HotelReview> findReviewsForHotel(@Param("hotelId") long hotelId);
}
