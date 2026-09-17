#include "app.hpp"

int main() {
    assistant::Application app("127.0.0.1", 8101);
    return app.run();
}
