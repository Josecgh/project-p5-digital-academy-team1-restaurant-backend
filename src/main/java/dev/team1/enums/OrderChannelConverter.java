package dev.team1.enums;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class OrderChannelConverter implements Converter<String, OrderChannel> {
    @Override
    public OrderChannel convert(String source) {
        return OrderChannel.fromValue(source);
    }
}
