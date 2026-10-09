package wot.gateway;

import org.springframework.stereotype.Component;

@Component
public class RulesState {

    private volatile boolean circuitLive = true;
    private volatile boolean lampOnByR1 = false;
    private volatile boolean lampManual = false;
    private volatile boolean thermostatManual = false;

    public boolean isCircuitLive() { return circuitLive; }
    public void setCircuitLive(boolean v) { circuitLive = v; }

    public boolean isLampOnByR1() { return lampOnByR1; }
    public void setLampOnByR1(boolean v) { lampOnByR1 = v; }

    public boolean isLampManual() { return lampManual; }
    public void setLampManual(boolean v) { lampManual = v; }

    public boolean isThermostatManual() { return thermostatManual; }
    public void setThermostatManual(boolean v) { thermostatManual = v; }

    public void resetManual() {
        lampManual = false;
        lampOnByR1 = false;
        thermostatManual = false;
    }
}