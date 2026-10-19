import os

# Select the development backend before application modules create their singleton.
# Ordinary tests must never connect to or clear a real Milvus collection.
os.environ["VECTOR_STORE_PROVIDER"] = "memory"
