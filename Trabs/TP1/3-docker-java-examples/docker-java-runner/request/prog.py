import sys

# Sum the numbers read from stdin, one per line
numbers = [float(line) for line in sys.stdin if line.strip()]
print(f"count: {len(numbers)}")
print(f"sum: {sum(numbers)}")
