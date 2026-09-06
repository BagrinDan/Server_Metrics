# Server_Metrics

==================
Planul laboratorului 1
==================

> **Tema:** Dezvoltarea unui sistem distribuit de colectare a telemetriei și a jurnalelor, bazat pe evenimente și pe modelul Publish/Subscribe.

> **Broker — Java**

**De ce:** Java permite implementarea foarte clară a arhitecturii serverului.

> **Pub 1 (Metrici CPU/RAM) — Go**

**De ce:** Go a fost conceput pentru dezvoltarea utilitarelor de sistem și a aplicațiilor concurente.

> **Pub 2 (Jurnale) — Python**

**De ce:** Python permite dezvoltarea rapidă a unui generator de jurnale sau a unui emulator de erori.

> **Sub 1 & Sub 2 (Afișarea metricilor și alarme) — Go**

**De ce:** În Go, cu ajutorul bibliotecilor `charmbracelet/bubbletea` sau `gizak/termui`, poate fi realizată o interfață TUI modernă în terminal, similară cu `htop` sau `btop`, care citește rapid datele JSON din socket și actualizează graficele în timp real.

> **K3s/Docker:**

**Containere:**

* `pub1` (Go) — colectarea metricilor
* `pub2` (Python) — colectarea/generarea jurnalelor
* `sub2` (Go) — afișarea jurnalelor

**Pod-uri:**

* `broker` (Java)
* `sub1` (Go) — interfața de monitorizare

> **Storage**

* **Transient (In-Memory)**
* **Persistent (On-Disk):**

  * Loki — pentru jurnale
  * InfluxDB — pentru metrici

## Publishers

The publisher implementations are in `pub1` (Go CPU/RAM metrics) and `pub2`
(Python structured logs). Both publish newline-delimited JSON over TCP to the
broker. Each event has `topic`, `timestamp`, `publisher`, and `payload` fields.
Set `BROKER_HOST`, `BROKER_PORT`, and `PUBLISH_INTERVAL_SECONDS` to configure
the destination and sampling interval.
