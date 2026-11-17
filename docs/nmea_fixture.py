"""Send deterministic GGA/GST observations to the Android emulator."""

import argparse
import socket
import time


def sentence(body):
    checksum = 0
    for character in body:
        checksum ^= ord(character)
    return f"${body}*{checksum:02X}\r\n".encode("ascii")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=9000)
    parser.add_argument("--quality", type=int, choices=(4, 5), default=4)
    parser.add_argument("--no-gst", action="store_true")
    args = parser.parse_args()
    with socket.create_server(("127.0.0.1", args.port)) as server:
        print(f"NMEA test feed on 127.0.0.1:{args.port}", flush=True)
        while True:
            connection, _ = server.accept()
            with connection:
                try:
                    while True:
                        epoch = time.strftime("%H%M%S", time.gmtime())
                        connection.sendall(sentence(
                            f"GNGGA,{epoch},5739.000,N,01441.400,E,{args.quality},08,0.9,220.0,M,38.0,M,,"
                        ))
                        if not args.no_gst:
                            connection.sendall(sentence(
                                f"GNGST,{epoch},0.05,0.04,0.03,0.0,0.03,0.04,0.08"
                            ))
                        time.sleep(1)
                except (BrokenPipeError, ConnectionResetError):
                    continue


if __name__ == "__main__":
    main()
