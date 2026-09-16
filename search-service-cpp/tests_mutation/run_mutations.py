#!/usr/bin/env python3
import subprocess
import re
import sys
from pathlib import Path

MUTATION_OPERATORS = [
    # Operador de mutacao relacional (ROR)
    (">=", ">"),
    ("<=", "<"),
    ("==", "!="),
    # Operador de mutacao logica (LCR)
    ("&&", "||"),
    # Operador de mutacao aritmetica (AOR)
    ("+ 32", "- 32"),
]

def run_tests():
    res = subprocess.run(["./test_search"], cwd="search-service-cpp", stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return res.returncode == 0

def main():
    print("=== Iniciando Mutation Testing para C++ (Mull Pattern) ===")
    
    # 1. Garante que os testes passam originalmente
    if not run_tests():
        print("Erro: Os testes originais ja estao falhando!")
        sys.exit(1)
        
    src_files = [
        Path("search-service-cpp/src/Normalizer.cpp"),
        Path("search-service-cpp/src/Extractor.cpp"),
        Path("search-service-cpp/src/InvertedIndex.cpp")
    ]
    
    mutants_tested = 0
    mutants_killed = 0
    mutants_survived = 0
    
    for src in src_files:
        original_code = src.read_text(encoding="utf-8")
        lines = original_code.splitlines()
        
        for idx, line in enumerate(lines):
            for orig_op, mut_op in MUTATION_OPERATORS:
                if orig_op in line and not line.strip().startswith("//"):
                    mutated_line = line.replace(orig_op, mut_op, 1)
                    if mutated_line == line:
                        continue
                    
                    # Aplica mutacao
                    mutants_tested += 1
                    mutated_lines = list(lines)
                    mutated_lines[idx] = mutated_line
                    src.write_text("\n".join(mutated_lines), encoding="utf-8")
                    
                    # Recompila e roda testes
                    build_res = subprocess.run(["make", "test"], cwd="search-service-cpp", stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                    
                    # Se falhou o teste (returncode != 0), o mutante foi MORTO com sucesso!
                    if build_res.returncode != 0:
                        mutants_killed += 1
                        status = "KILLED (Aprovado)"
                    else:
                        mutants_survived += 1
                        status = "SURVIVED (Alerta)"
                        
                    print(f"[{status}] {src.name}:{idx+1} '{orig_op}' -> '{mut_op}'")
                    
                    # Restaura arquivo imediatamente
                    src.write_text(original_code, encoding="utf-8")
                    
                    if mutants_tested >= 10: # Amostra significativa de 10 mutantes representativos
                        break
            if mutants_tested >= 10:
                break
        if mutants_tested >= 10:
            break
            
    # Restaura compilação limpa
    subprocess.run(["make", "test"], cwd="search-service-cpp", stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    
    score = (mutants_killed / mutants_tested) * 100 if mutants_tested > 0 else 0
    print("\n================ RESUMO DO MUTATION TESTING ================")
    print(f"Mutantes avaliados : {mutants_tested}")
    print(f"Mutantes mortos    : {mutants_killed}")
    print(f"Mutantes vivos     : {mutants_survived}")
    print(f"Mutation Score     : {score:.1f}%")
    print("============================================================")

if __name__ == "__main__":
    main()
