#pragma once

#include <string>
#include "json.hpp"

namespace assistant {

using json = nlohmann::json;

struct InferenceResult {
    int httpStatus;
    json body;
};

} // namespace assistant
