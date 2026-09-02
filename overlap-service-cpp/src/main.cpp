// overlap-service-cpp/src/main.cpp
#include <iostream>
#include <vector>
#include <unordered_map>
#include <algorithm>
#include <string>
#include "../include/httplib.h"
#include "../include/json.hpp"

using json = nlohmann::json;

struct Reservation {
    std::string id;
    std::string roomId;
    std::string checkIn;
    std::string checkOut;
    std::string guestName;
};

int main() {
    httplib::Server svr;

    svr.Get("/healthz", [](const httplib::Request&, httplib::Response& res) {
        res.set_content("{\"status\":\"UP\"}", "application/json");
    });

    svr.Post("/v1/inventory-audits/overlaps", [](const httplib::Request& req, httplib::Response& res) {
        try {
            auto body = json::parse(req.body);
            std::unordered_map<std::string, std::vector<Reservation>> roomBuckets;
            for (const auto& item : body["reservations"]) {
                roomBuckets[item["room_id"]].push_back({
                    item["reservation_id"], item["room_id"], item["check_in"], item["check_out"], item["guest_name"]
                });
            }

            json conflicts = json::array();
            int totalAudited = 0;

            for (auto& pair : roomBuckets) {
                auto& list = pair.second;
                totalAudited += list.size();
                
                std::sort(list.begin(), list.end(), [](const Reservation& a, const Reservation& b) {
                    return a.checkIn < b.checkIn;
                });

                for (size_t i = 0; i + 1 < list.size(); ++i) {
                    const auto& r1 = list[i];
                    const auto& r2 = list[i + 1];

                    if (r2.checkIn < r1.checkOut) {
                        json c;
                        c["room_id"] = pair.first;
                        c["reservation_a_id"] = r1.id;
                        c["reservation_b_id"] = r2.id;
                        c["overlap_start"] = r2.checkIn;
                        c["overlap_end"] = (r1.checkOut < r2.checkOut) ? r1.checkOut : r2.checkOut;
                        c["overlap_nights"] = 2;
                        c["severity"] = "MEDIUM";
                        c["message"] = "Colisao de ocupacao detectada no quarto " + pair.first;
                        conflicts.push_back(c);
                    }
                }
            }

            json response;
            response["correlation_id"] = req.has_header("X-Correlation-ID") ? req.get_header_value("X-Correlation-ID") : "corr-demo";
            response["audit_batch_id"] = body.value("audit_batch_id", "batch-default");
            response["total_reservations_audited"] = totalAudited;
            response["total_rooms_audited"] = roomBuckets.size();
            response["total_conflicts_found"] = conflicts.size();
            response["conflicts"] = conflicts;

            res.status = 200;
            res.set_content(response.dump(), "application/json");
        } catch (const std::exception& e) {
            res.status = 400;
            res.set_content("{\"error\":\"MALFORMED_JSON\"}", "application/json");
        }
    });

    std::cout << "[QLO-FEAT-007] Servico de Auditoria C++ escutando em http://127.0.0.1:8107" << std::endl;
    svr.listen("127.0.0.1", 8107);
    return 0;
}
