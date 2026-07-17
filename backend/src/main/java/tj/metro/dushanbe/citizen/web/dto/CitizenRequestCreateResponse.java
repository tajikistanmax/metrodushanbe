package tj.metro.dushanbe.citizen.web.dto;

/** Ответ создания: секрет отслеживания возвращается только в этом ответе. */
public record CitizenRequestCreateResponse(
        CitizenRequestPublicDto request,
        String trackingToken) {
}
