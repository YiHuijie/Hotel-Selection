package edu.polyu.comp5511.hotelselection.api;

import edu.polyu.comp5511.hotelselection.domain.Hotel;
import edu.polyu.comp5511.hotelselection.domain.HotelDetail;
import edu.polyu.comp5511.hotelselection.persistence.HotelMapper;
import edu.polyu.comp5511.hotelselection.persistence.ReviewMapper;
import edu.polyu.comp5511.hotelselection.persistence.RoomOfferMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {
    private final HotelMapper hotelMapper;
    private final RoomOfferMapper roomOfferMapper;
    private final ReviewMapper reviewMapper;

    public HotelController(HotelMapper hotelMapper, RoomOfferMapper roomOfferMapper, ReviewMapper reviewMapper) {
        this.hotelMapper = hotelMapper;
        this.roomOfferMapper = roomOfferMapper;
        this.reviewMapper = reviewMapper;
    }

    @GetMapping("/{hotelId}")
    public HotelDetail detail(@PathVariable long hotelId) {
        Hotel hotel = hotelMapper.findById(hotelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该酒店。"));
        List<String> amenities = hotel.amenities() == null || hotel.amenities().isBlank()
                ? List.of()
                : java.util.Arrays.stream(hotel.amenities().split(",")).map(String::trim).toList();
        return new HotelDetail(hotel, amenities, roomOfferMapper.findByHotelId(hotelId),
                reviewMapper.findReviewsForHotel(hotelId));
    }

    @GetMapping
    public java.util.List<Hotel> search(
            @RequestParam String city,
            @RequestParam java.time.LocalDate checkIn,
            @RequestParam java.time.LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退房日期必须晚于入住日期。");
        }
        return hotelMapper.findAvailable(
                city, checkIn, checkOut, java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut));
    }
}
