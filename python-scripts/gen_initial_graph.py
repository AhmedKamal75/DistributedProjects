import random
def generate_test(num_nodes=100, num_edges=1000):
    edges = set()
    while len(edges) < num_edges:
        u = random.randint(0, num_nodes - 1)
        v = random.randint(0, num_nodes - 1)
        if u != v:
            edges.add((u, v))
    return edges

def export_edges(edges, filename):
    with open(filename, 'w') as f:
        for u, v in edges:
            f.write(f"{u} {v}\n")
        f.write("S\n")



if __name__ == "__main__":
    export_edges(generate_test(), "../graph/initial_graph.txt")
    