#pragma once

#include <string>
#include <regex>
#include <vector>
#include <algorithm>
#include "intent.hpp"

namespace assistant::domain {

struct ClassificationResult {
    Intent intent;
    double confidence;
};

class QueryClassifier {
public:
    static ClassificationResult classify(const std::string& normQuery) {
        // -------------------------------------------------------------
        // 0. Detect Out-of-Domain / Operational Services Conflict (RN-008)
        // -------------------------------------------------------------
        // Governança / Staff / Limpeza de quartos (serviço interno, não reserva):
        bool hasHousekeeping = (normQuery.find("camareira") != std::string::npos ||
                                normQuery.find("faxin") != std::string::npos ||
                                normQuery.find("limpeza") != std::string::npos ||
                                normQuery.find("troca de toalha") != std::string::npos ||
                                normQuery.find("toalha") != std::string::npos ||
                                normQuery.find("sabonete") != std::string::npos ||
                                normQuery.find("lencol") != std::string::npos ||
                                normQuery.find("travesseiro") != std::string::npos);

        // Alimentos & Bebidas (quando a pergunta é sobre refeições/menu, não reserva com café):
        bool hasFoodAndBeverage = (normQuery.find("cardapio") != std::string::npos ||
                                   normQuery.find("almoco") != std::string::npos ||
                                   normQuery.find("jantar") != std::string::npos ||
                                   normQuery.find("garcom") != std::string::npos ||
                                   normQuery.find("prato do dia") != std::string::npos ||
                                   normQuery.find("restaurante") != std::string::npos);

        // Manutenção / Defeitos operacionais:
        bool hasMaintenanceIssue = (normQuery.find("conserto") != std::string::npos ||
                                     normQuery.find("quebrado") != std::string::npos ||
                                     normQuery.find("nao funciona") != std::string::npos ||
                                     normQuery.find("defeito") != std::string::npos ||
                                     normQuery.find("lampada") != std::string::npos);

        // Termos externos gerais (previsão do tempo, turismo externo, etc):
        bool hasGeneralExternal = (normQuery.find("previsao do tempo") != std::string::npos ||
                                   normQuery.find("tempo para a praia") != std::string::npos ||
                                   normQuery.find("farmacia") != std::string::npos ||
                                   normQuery.find("supermercado") != std::string::npos);

        bool isOutOfDomain = hasHousekeeping || hasFoodAndBeverage || hasMaintenanceIssue || hasGeneralExternal;

        // -------------------------------------------------------------
        // 1. Check for RESERVATION_LOOKUP (RN-007)
        // -------------------------------------------------------------
        std::regex resCodeRegex("res-[0-9a-zA-Z]+");
        bool hasResCode = std::regex_search(normQuery, resCodeRegex);
        bool hasResLookupTerms = (normQuery.find("minha reserva") != std::string::npos ||
                                  normQuery.find("status da reserva") != std::string::npos ||
                                  normQuery.find("consultar reserva") != std::string::npos ||
                                  normQuery.find("localizador da reserva") != std::string::npos ||
                                  normQuery.find("codigo de reserva") != std::string::npos);

        if (hasResCode || hasResLookupTerms) {
            double conf = 0.85;
            if (hasResCode) conf += 0.07; // 0.92 com código
            if (hasResLookupTerms) conf += 0.03;
            if (conf > 0.95) conf = 0.95;
            return {Intent::RESERVATION_LOOKUP, conf};
        }

        // -------------------------------------------------------------
        // 2. Check for POLICY_QUERY (RN-005)
        // -------------------------------------------------------------
        bool hasCancellation = (normQuery.find("cancelamento") != std::string::npos ||
                                normQuery.find("reembolso") != std::string::npos ||
                                normQuery.find("multa") != std::string::npos ||
                                normQuery.find("no-show") != std::string::npos ||
                                normQuery.find("noshow") != std::string::npos ||
                                normQuery.find("desistir") != std::string::npos ||
                                normQuery.find("estorno") != std::string::npos);

        bool hasCheckinRules = (normQuery.find("check-in tardio") != std::string::npos ||
                                normQuery.find("checkin tardio") != std::string::npos ||
                                normQuery.find("horario de check-in") != std::string::npos ||
                                normQuery.find("horario de checkin") != std::string::npos ||
                                normQuery.find("limite de check-in") != std::string::npos ||
                                normQuery.find("politica de check-in") != std::string::npos ||
                                normQuery.find("horario de checkout") != std::string::npos ||
                                normQuery.find("horario de check-out") != std::string::npos ||
                                normQuery.find("late checkout") != std::string::npos ||
                                normQuery.find("early check-in") != std::string::npos);

        bool hasGeneralPolicy = (normQuery.find("politica") != std::string::npos ||
                                 normQuery.find("regras do hotel") != std::string::npos ||
                                 normQuery.find("aceita pet") != std::string::npos ||
                                 normQuery.find("permite animais") != std::string::npos ||
                                 normQuery.find("politica de fumantes") != std::string::npos);

        if (hasCancellation || hasCheckinRules || (hasGeneralPolicy && normQuery.find("quarto") == std::string::npos)) {
            double conf = 0.88;
            if (hasCancellation) conf += 0.02; // 0.90
            if (hasCheckinRules) conf += 0.02; // 0.90
            return {Intent::POLICY_QUERY, conf};
        }

        // -------------------------------------------------------------
        // 3. Out-of-Domain Filter (RN-008: confidence < 0.60 e UNKNOWN)
        // -------------------------------------------------------------
        // Se há termos de outros setores do hotel (camareiras, cardápio, manutenção)
        // a intenção é estritamente UNKNOWN com baixa confiança (35.0%).
        if (isOutOfDomain) {
            return {Intent::UNKNOWN, 0.35};
        }

        // -------------------------------------------------------------
        // 4. Dynamic Weighted Scoring for AVAILABILITY_QUERY (RN-002)
        // -------------------------------------------------------------
        double score = 0.0;

        // 4.1 Intenção e verbos de consulta / reserva de acomodação:
        bool hasExplicitBooking = (normQuery.find("tem quarto") != std::string::npos ||
                                   normQuery.find("tem vaga") != std::string::npos ||
                                   normQuery.find("tem suite") != std::string::npos ||
                                   normQuery.find("tem acomodacao") != std::string::npos ||
                                   normQuery.find("ha quarto") != std::string::npos ||
                                   normQuery.find("ha vaga") != std::string::npos ||
                                   normQuery.find("preciso de") != std::string::npos ||
                                   normQuery.find("gostaria de") != std::string::npos ||
                                   normQuery.find("queria") != std::string::npos ||
                                   normQuery.find("reservar") != std::string::npos ||
                                   normQuery.find("reserva de") != std::string::npos ||
                                   normQuery.find("fazer reserva") != std::string::npos ||
                                   normQuery.find("disponivel") != std::string::npos ||
                                   normQuery.find("disponibilidade") != std::string::npos ||
                                   normQuery.find("vaga") != std::string::npos ||
                                   normQuery.find("vagas") != std::string::npos ||
                                   normQuery.find("hospedar") != std::string::npos ||
                                   normQuery.find("hospedagem") != std::string::npos ||
                                   normQuery.find("diaria") != std::string::npos ||
                                   normQuery.find("valor da estadia") != std::string::npos ||
                                   normQuery.find("quanto custa") != std::string::npos);

        bool hasLead = (normQuery.find("tem ") != std::string::npos ||
                        normQuery.find("ha ") != std::string::npos ||
                        normQuery.find("possui ") != std::string::npos ||
                        normQuery.find("existe ") != std::string::npos);

        if (hasExplicitBooking) {
            score = 0.85; // RN-002: termos como 'vaga', 'tem quarto', 'disponivel' classificam como AVAILABILITY_QUERY com score >= 0.85
        } else if (hasLead) {
            score = 0.20;
        }

        // 4.2 Tipo de acomodação:
        bool hasRoomType = (normQuery.find("deluxe") != std::string::npos ||
                            normQuery.find("suite") != std::string::npos ||
                            normQuery.find("standard") != std::string::npos ||
                            normQuery.find("executiva") != std::string::npos ||
                            normQuery.find("presidencial") != std::string::npos ||
                            normQuery.find("chale") != std::string::npos ||
                            normQuery.find("bangalo") != std::string::npos ||
                            normQuery.find("apartamento") != std::string::npos ||
                            normQuery.find("quarto") != std::string::npos ||
                            normQuery.find("leito") != std::string::npos ||
                            normQuery.find("dormitorio") != std::string::npos ||
                            normQuery.find("acomodacao") != std::string::npos);

        if (hasRoomType) {
            if (hasExplicitBooking) {
                score += 0.05; // Reforço de acomodação explícita
            } else {
                score += 0.35; // Apenas citação de acomodação sem intenção de reserva (ex: "alguma suíte com vista pra praia")
            }
            if (normQuery.find("deluxe") != std::string::npos ||
                normQuery.find("suite") != std::string::npos ||
                normQuery.find("executiva") != std::string::npos ||
                normQuery.find("standard") != std::string::npos) {
                if (hasExplicitBooking) {
                    score += 0.03; // Categoria específica
                }
            }
        }

        // 4.3 Slot temporal:
        bool hasTemporal = (normQuery.find("hoje") != std::string::npos ||
                            normQuery.find("amanha") != std::string::npos ||
                            normQuery.find("depois de amanha") != std::string::npos ||
                            normQuery.find("fim de semana") != std::string::npos ||
                            normQuery.find("semana que vem") != std::string::npos ||
                            normQuery.find("proxima ") != std::string::npos ||
                            normQuery.find("noite") != std::string::npos ||
                            normQuery.find("dias") != std::string::npos ||
                            normQuery.find("daqui a") != std::string::npos);

        if (hasTemporal) {
            score += 0.05;
        }

        // 4.4 Slot de hóspedes:
        bool hasGuests = (std::regex_search(normQuery, std::regex("\\b[0-9]+\\s*(?:pessoas?|adultos?|hospedes?)")) ||
                          normQuery.find("casal") != std::string::npos ||
                          normQuery.find("solteiro") != std::string::npos);

        if (hasGuests) {
            score += 0.05;
        }

        // Arredondamento e teto
        score = std::min(0.98, std::max(0.0, score));

        // -------------------------------------------------------------
        // 5. Limiar de Decisão da RFC (Seção 5: confiança >= 0.80)
        // -------------------------------------------------------------
        if (score >= 0.80) {
            return {Intent::AVAILABILITY_QUERY, score};
        }

        // Se a query citou acomodação mas não tem intenção de reserva ou está incompleta
        // (ex: "alguma suite com vista pra praia"), score fica entre 0.35 e 0.60.
        // Pela Seção 5 da RFC, score < 0.80 cai em UNKNOWN com a confiança real calculada!
        double unkConf = (score > 0.0) ? score : 0.35;
        return {Intent::UNKNOWN, unkConf};
    }
};

} // namespace assistant::domain
