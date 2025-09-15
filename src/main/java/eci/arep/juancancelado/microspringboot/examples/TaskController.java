package eci.arep.juancancelado.microspringboot.examples;

import eci.arep.juancancelado.model.Task;
import eci.arep.juancancelado.microspringboot.annotations.GetMapping;
import eci.arep.juancancelado.microspringboot.annotations.PostMapping;
import eci.arep.juancancelado.microspringboot.annotations.RequestBody;
import eci.arep.juancancelado.microspringboot.annotations.RestController;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
public class TaskController {
    private final List<Task> tasks = new CopyOnWriteArrayList<>();

    @GetMapping("/api/tasks")
    public List<Task> getTask() {
        return tasks;
    }

    @PostMapping("/api/tasks")
    public Map<String, String> addTask(@RequestBody Map<String, String> data) {
        if (data.containsKey("name") && data.containsKey("type")) {
            tasks.add(new Task(data.get("name"), data.get("type")));
            return Map.of("message", "Task added successfully");
        }
        return Map.of("error", "Missing fields");
    }
}
