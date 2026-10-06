# Allocates memory in 10 MB chunks until the container's 256 MB limit is exceeded (exit 137)
chunks = []
while True:
    chunks.append(bytearray(10 * 1024 * 1024))
    print(f"allocated: {len(chunks) * 10} MB", flush=True)