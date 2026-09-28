package cn.bugstack.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.io.Serializable;

/** Demo reset has no caller-controlled fields; identity is accepted only from the verified JWT subject. */
public class AgentDemoResetRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonAnySetter
    public void rejectUnexpectedField(String fieldName, Object ignored) {
        throw new IllegalArgumentException("Unsupported request field: " + fieldName);
    }

}
