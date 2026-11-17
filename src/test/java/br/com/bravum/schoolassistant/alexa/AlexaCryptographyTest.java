package br.com.bravum.schoolassistant.alexa;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AlexaCryptographyTest {
    private static final String AES = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));
    @Test void hmacAndAesGcmAreDeterministicAndReversibleAsAppropriate() {
        AlexaService s=new AlexaService(new JdbcTemplate(),"a".repeat(40),AES);
        assertEquals(s.hash("USER:amazon-test"),s.hash("USER:amazon-test"));
        assertNotEquals(s.hash("CODE:12345678"),s.hash("USER:12345678"));
        String encrypted=s.open(encryptTestValue(s,"amzn1.ask.account.TESTUSER"));
        assertEquals("amzn1.ask.account.TESTUSER",encrypted);
    }
    private String encryptTestValue(AlexaService service,String value) {
        try {
            var method=AlexaService.class.getDeclaredMethod("seal",String.class);
            method.setAccessible(true);
            return (String)method.invoke(service,value);
        }catch(Exception e){throw new AssertionError(e);}
    }
}
